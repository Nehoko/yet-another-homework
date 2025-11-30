package ge.imikhailov.omno.cache.multilevel;

import ge.imikhailov.omno.cache.pubsub.CacheInvalidationPublisher;
import ge.imikhailov.omno.metrics.CacheMetricsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class MultiLevelCacheManagerTest {

    @Test
    void returnsCompositeWhenBothLevelsPresent() {
        ConcurrentMapCacheManager l1 = new ConcurrentMapCacheManager("price");
        ConcurrentMapCacheManager l2 = new ConcurrentMapCacheManager("price");

        MultiLevelCacheManager mgr = new MultiLevelCacheManager(
                l1,
                l2,
                mock(CacheInvalidationPublisher.class),
                Duration.ofSeconds(5),
                new CacheMetricsFactory(meterRegistry())
        );

        Cache cache = mgr.getCache("price");
        assertThat(cache).isInstanceOf(MultiLevelCache.class);
    }

    @Test
    void fallsBackToSingleLevelIfOnlyOneExists() {
        ConcurrentMapCacheManager l1 = new ConcurrentMapCacheManager("price");
        org.springframework.cache.CacheManager l2 = mock(org.springframework.cache.CacheManager.class);
        when(l2.getCache("price")).thenReturn(null);
        when(l2.getCacheNames()).thenReturn(java.util.Collections.emptySet());

        MultiLevelCacheManager mgr = new MultiLevelCacheManager(
                l1,
                l2,
                null,
                Duration.ZERO,
                new CacheMetricsFactory(meterRegistry())
        );

        Cache cache = mgr.getCache("price");
        assertThat(cache).isNotNull();
        assertThat(cache).isNotInstanceOf(MultiLevelCache.class);
        assertThat(mgr.getCacheNames()).contains("price");
    }

    private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry() {
        return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    }
}
