package ge.imikhailov.omno.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CacheMetricsFactory {

    private final MeterRegistry meterRegistry;

    public CacheMetrics createCacheMetric(final String cacheName,
                                          final ConcurrentHashMap<Object, CompletableFuture<Object>> inFlight) {
        final CacheMetrics cacheMetrics = new CacheMetrics(
                counter(cacheName, "omno.cache.gets", "level", "L1", "outcome", "hit"),
                counter(cacheName, "omno.cache.gets", "level", "L1", "outcome", "miss"),
                counter(cacheName, "omno.cache.gets", "level", "L2", "outcome", "hit"),
                counter(cacheName, "omno.cache.gets", "level", "L2", "outcome", "miss"),
                counter(cacheName, "omno.cache.refresh", "phase", "started"),
                counter(cacheName, "omno.cache.refresh", "phase", "success"),
                counter(cacheName, "omno.cache.refresh", "phase", "failure"),
                counter(cacheName, "omno.cache.refresh", "phase", "duplicate_suppressed"),
                counter(cacheName, "omno.cache.evictions"),
                counter(cacheName, "omno.cache.clears"),
                timer(cacheName));

        Gauge.builder("omno.cache.inflight", inFlight, ConcurrentHashMap::size)
                .tag("cache", cacheName)
                .register(this.meterRegistry);
        return cacheMetrics;
    }


    private Counter counter(final String cacheName, final String metricName, final String... tags) {
        final Counter.Builder b = Counter.builder(metricName).tag("cache", cacheName);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            b.tag(tags[i], tags[i + 1]);
        }
        return b.register(this.meterRegistry);
    }

    private Timer timer(final String cacheName) {
        return Timer.builder("omno.cache.loader")
                .tag("cache", cacheName)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(this.meterRegistry);
    }
}
