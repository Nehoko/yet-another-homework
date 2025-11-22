package ge.imikhailov.omno.cache;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

@RequiredArgsConstructor
public class MultiLevelCache implements Cache {

    private final String name;
    private final Cache l1;
    private final Cache l2;
    private final @Nullable CacheInvalidationPublisher publisher;

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
        ValueWrapper v1 = l1.get(key);
        if (v1 != null) {
            return v1;
        }
        ValueWrapper v2 = l2.get(key);
        if (v2 != null) {
            Object value = v2.get();
            // backfill L1 if we found value in L2
            if (value != null) {
                l1.put(key, value);
            }
            return v2;
        }
        return null;
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        T v1 = l1.get(key, type);
        if (v1 != null) {
            return v1;
        }
        T v2 = l2.get(key, type);
        if (v2 != null) {
            l1.put(key, v2);
        }
        return v2;
    }

    @Override
    public <T> @Nullable T get(Object key, Callable<T> loader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return (T) cached.get();
        }
        try {
            T value = loader.call();
            if (value != null) {
                l1.put(key, value);
                l2.put(key, value);
            }
            return value;
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, loader, ex);
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        l1.put(key, value);
        l2.put(key, value);
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
