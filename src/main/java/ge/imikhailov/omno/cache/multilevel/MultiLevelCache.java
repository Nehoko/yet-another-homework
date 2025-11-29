package ge.imikhailov.omno.cache.multilevel;

import ge.imikhailov.omno.cache.pubsub.CacheInvalidationPublisher;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
public class MultiLevelCache implements Cache {

    private final String name;
    private final Cache l1;
    private final Cache l2;
    private final @Nullable CacheInvalidationPublisher publisher;
    private final Duration softTtl;
    // Tracks in-flight loads per key to provide single-flight behavior and avoid duplicate DB hits
    private final ConcurrentHashMap<Object, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

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
            final Object unwrapped = unwrap(v1.get());
            return new SimpleValueWrapper(unwrapped);
        }
        final ValueWrapper v2 = l2.get(key);
        if (v2 != null) {
            final Object value = v2.get();
            // backfill L1 if we found value in L2
            if (value != null) {
                l1.put(key, value);
            }
            final Object unwrapped = unwrap(value);
            return new SimpleValueWrapper(unwrapped);
        }
        return null;
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        final ValueWrapper v1 = l1.get(key);
        if (v1 != null) {
            final T unwrapped = unwrap(v1.get());
            if (unwrapped == null || type == null || type.isInstance(unwrapped)) {
                return unwrapped;
            }
            return null;
        }
        final ValueWrapper v2 = l2.get(key);
        if (v2 != null) {
            final Object raw = v2.get();
            if (raw != null) {
                l1.put(key, raw);
            }
            final T unwrapped = unwrap(raw);
            if (unwrapped == null || type == null || type.isInstance(unwrapped)) {
                return unwrapped;
            }
        }
        return null;
    }

    @Override
    public <T> @Nullable T get(Object key, Callable<T> loader) {
        try {
            // Try L1
            final ValueWrapper v1 = l1.get(key);
            final CachedEntry e1 = toEntry(v1 != null ? v1.get() : null);
            if (!isStale(e1)) {
                return unwrap(e1);
            }
            // Try L2
            final ValueWrapper v2 = l2.get(key);
            if (v2 == null) {
                return (T) triggerRefreshAsync(key, loader).get();
            }
            final CachedEntry e2 = toEntry(v2.get());
            if (e2 == null) {
                return (T) triggerRefreshAsync(key, loader).get();
            }
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
            return existing;
        }
        CompletableFuture.runAsync(() -> {
            try {
                T value = loader.call();
                if (value != null) {
                    CachedEntry e = new CachedEntry(value, System.currentTimeMillis());
                    l1.put(key, e);
                    l2.put(key, e);
                }
                newFuture.complete(value);
            } catch (Exception ex) {
                newFuture.completeExceptionally(ex);
            } finally {
                inFlight.remove(key, newFuture);
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
        if (publisher != null) {
            publisher.publishEvict(name, key);
        }
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
        if (publisher != null) {
            publisher.publishClear(name);
        }
    }
}
