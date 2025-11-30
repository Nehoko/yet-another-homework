package ge.imikhailov.omno.cache.multilevel;

import ge.imikhailov.omno.cache.pubsub.CacheInvalidationPublisher;
import ge.imikhailov.omno.metrics.CacheMetrics;
import ge.imikhailov.omno.metrics.CacheMetricsFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiLevelCache implements Cache {

    private final String name;
    private final Cache l1;
    private final Cache l2;
    private final Duration softTtl;
    private final @Nullable CacheInvalidationPublisher publisher;
    private final CacheMetrics cacheMetrics;
    // Tracks in-flight loads per key to provide single-flight behavior and avoid duplicate DB hits
    private final ConcurrentHashMap<Object, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    // Tracks how many loads are currently executing; exposed as a gauge (avoids NaN export issues)
    private final AtomicInteger inFlightGauge = new AtomicInteger();

    public MultiLevelCache(String name, Cache l1, Cache l2, Duration softTtl, @Nullable CacheInvalidationPublisher publisher, CacheMetricsFactory cacheMetricsFactory) {
        this.name = name;
        this.l1 = l1;
        this.l2 = l2;
        this.softTtl = softTtl;
        this.publisher = publisher;
        this.cacheMetrics = cacheMetricsFactory.createCacheMetric(name, inFlightGauge);
    }


    private boolean isStale(@Nullable CachedEntry e) {
        if (e == null) return true;
        if (softTtl.isZero() || softTtl.isNegative()) return false;
        long age = System.currentTimeMillis() - e.getWriteTimeMs();
        return age >= softTtl.toMillis();
    }

    private static @Nullable CachedEntry toEntry(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof CachedEntry ce) return ce;
        return new CachedEntry(v, System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private static @Nullable <T> T unwrap(@Nullable Object v) {
        if (v == null) return null;
        if (v instanceof CachedEntry ce) return (T) ce.getValue();
        return (T) v;
    }

    @Override
    public String getName() {
        // Explicit composite cache name
        return name;
    }

    @Override
    public Object getNativeCache() {
        // Expose this composite as the native cache
        return this;
    }

    @Override
    public @Nullable ValueWrapper get(Object key) {
        final ValueWrapper v1 = l1.get(key);
        if (v1 != null) {
            cacheMetrics.l1HitIncrement();
            final Object unwrapped = unwrap(v1.get());
            return new SimpleValueWrapper(unwrapped);
        }
        cacheMetrics.l1MissIncrement();
        final ValueWrapper v2 = l2.get(key);
        if (v2 != null) {
            cacheMetrics.l2HitIncrement();
            final Object value = v2.get();
            // backfill L1 if we found value in L2
            if (value != null) {
                l1.put(key, value);
            }
            final Object unwrapped = unwrap(value);
            return new SimpleValueWrapper(unwrapped);
        }
        cacheMetrics.l2MissIncrement();
        return null;
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        final ValueWrapper v1 = l1.get(key);
        if (v1 != null) {
            cacheMetrics.l1HitIncrement();
            final T unwrapped = unwrap(v1.get());
            if (unwrapped == null || type == null || type.isInstance(unwrapped)) {
                return unwrapped;
            }
            return null;
        }
        cacheMetrics.l1MissIncrement();
        final ValueWrapper v2 = l2.get(key);
        if (v2 != null) {
            cacheMetrics.l2HitIncrement();
            final Object raw = v2.get();
            if (raw != null) {
                l1.put(key, raw);
            }
            final T unwrapped = unwrap(raw);
            if (unwrapped == null || type == null || type.isInstance(unwrapped)) {
                return unwrapped;
            }
        }
        cacheMetrics.l2MissIncrement();
        return null;
    }

    @Override
    public <T> @Nullable T get(Object key, Callable<T> loader) {
        try {
            // Try L1
            final ValueWrapper v1 = l1.get(key);
            final CachedEntry e1 = toEntry(v1 != null ? v1.get() : null);
            if (!isStale(e1)) {
                if (e1 != null) cacheMetrics.l1HitIncrement();
                else cacheMetrics.l1MissIncrement();
                return unwrap(e1);
            }
            if (e1 != null) {
                // Stale in L1 counts as miss in terms of freshness
                cacheMetrics.l1MissIncrement();
            }
            // Try L2
            final ValueWrapper v2 = l2.get(key);
            if (v2 == null) {
                cacheMetrics.l2MissIncrement();
                return (T) triggerRefreshAsync(key, loader).get();
            }
            final CachedEntry e2 = toEntry(v2.get());
            if (e2 == null) {
                cacheMetrics.l2MissIncrement();
                return (T) triggerRefreshAsync(key, loader).get();
            }
            cacheMetrics.l2HitIncrement();
            l1.put(key, e2);
            if (isStale(e2)) {
                return unwrap(triggerRefreshAsync(key, loader));
            }
            return unwrap(e2);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ValueRetrievalException(key, loader, ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new ValueRetrievalException(key, loader, cause);
        }
    }

    private <T> CompletableFuture<Object> triggerRefreshAsync(Object key, Callable<T> loader) {
        // Try to become the refresher; if a load is already in-flight, do nothing
        final CompletableFuture<Object> newFuture = new CompletableFuture<>();
        final CompletableFuture<Object> existing = inFlight.putIfAbsent(key, newFuture);
        if (existing != null) {
            cacheMetrics.duplicateSuppressedIncrement();
            return existing;
        }
        inFlightGauge.incrementAndGet();

        CompletableFuture.runAsync(() -> {
            try {
                cacheMetrics.refreshStartedIncrement();
                final T value = cacheMetrics.callInTimer(() -> {
                    T v = loader.call();
                    if (v != null) {
                        CachedEntry e = new CachedEntry(v, System.currentTimeMillis());
                        l1.put(key, e);
                        l2.put(key, e);
                    }
                    return v;
                });
                cacheMetrics.refreshSuccessIncrement();
                newFuture.complete(value);
            } catch (Exception ex) {
                cacheMetrics.refreshFailureIncrement();
                newFuture.completeExceptionally(ex);
            } finally {
                inFlight.remove(key, newFuture);
                inFlightGauge.decrementAndGet();
            }
        });
        return newFuture;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (value == null) {
            l1.put(key, null);
            l2.put(key, null);
            return;
        }
        CachedEntry e = new CachedEntry(value, System.currentTimeMillis());
        l1.put(key, e);
        l2.put(key, e);
    }

    @Override
    public void evict(Object key) {
        l1.evict(key);
        l2.evict(key);
        cacheMetrics.evictionIncrement();
        if (publisher != null) {
            publisher.publishEvict(name, key);
        }
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
        cacheMetrics.clearIncrement();
        if (publisher != null) {
            publisher.publishClear(name);
        }
    }
}
