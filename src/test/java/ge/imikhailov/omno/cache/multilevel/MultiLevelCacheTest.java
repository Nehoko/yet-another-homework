package ge.imikhailov.omno.cache.multilevel;

import ge.imikhailov.omno.cache.pubsub.CacheInvalidationPublisher;
import ge.imikhailov.omno.metrics.CacheMetricsFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MultiLevelCacheTest {

    @Test
    void l2Miss_triggersAsyncRefresh_andPopulatesBothLevels_singleFlight() throws Exception {
        var l1 = new ConcurrentMapCache("price-l1");
        var l2 = new ConcurrentMapCache("price-l2");
        var publisher = Mockito.mock(CacheInvalidationPublisher.class);
        var cache = new MultiLevelCache(
                "price",
                l1,
                l2,
                Duration.ofSeconds(5),
                publisher,
                new CacheMetricsFactory(new SimpleMeterRegistry())
        );

        final String key = "k1";
        final AtomicInteger calls = new AtomicInteger();

        Callable<String> loader = () -> {
            calls.incrementAndGet();
            TimeUnit.MILLISECONDS.sleep(50);
            return "v1";
        };

        // Run two concurrent gets; must invoke loader only once (single-flight)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> f1 = pool.submit(() -> cache.get(key, loader));
        Future<String> f2 = pool.submit(() -> cache.get(key, loader));
        assertThat(f1.get(5, TimeUnit.SECONDS)).isEqualTo("v1");
        assertThat(f2.get(5, TimeUnit.SECONDS)).isEqualTo("v1");
        pool.shutdownNow();

        assertThat(calls.get()).isEqualTo(1);

        // Both levels should be populated with CachedEntry wrapper
        assertThat(l1.get(key)).isNotNull();
        assertThat(l2.get(key)).isNotNull();
        assertThat(cache.get(key).get()).isEqualTo("v1");

        verifyNoMoreInteractions(publisher);
    }

    @Test
    void evictAndClear_publishToPublisher() {
        var l1 = new ConcurrentMapCache("price-l1");
        var l2 = new ConcurrentMapCache("price-l2");
        var publisher = Mockito.mock(CacheInvalidationPublisher.class);
        var cache = new MultiLevelCache(
                "price",
                l1,
                l2,
                Duration.ofSeconds(5),
                publisher,
                new CacheMetricsFactory(new SimpleMeterRegistry())
        );

        cache.put("k2", "v2");
        cache.evict("k2");
        verify(publisher).publishEvict("price", "k2");

        cache.clear();
        verify(publisher).publishClear("price");
    }
}
