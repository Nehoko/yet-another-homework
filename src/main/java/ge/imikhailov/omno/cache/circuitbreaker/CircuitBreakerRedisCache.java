package ge.imikhailov.omno.cache.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

@RequiredArgsConstructor
public class CircuitBreakerRedisCache implements Cache {

    private final Cache redisCache;
    private final CircuitBreaker circuitBreaker;

    @Override
    public String getName() {
        return redisCache.getName();
    }

    @Override
    public Object getNativeCache() {
        return redisCache.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        return CircuitBreaker
                .decorateSupplier(circuitBreaker, () -> redisCache.get(key))
                .get();
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return CircuitBreaker
                .decorateSupplier(circuitBreaker, () -> redisCache.get(key, type))
                .get();
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return CircuitBreaker
                .decorateSupplier(circuitBreaker, () -> redisCache.get(key, valueLoader))
                .get();
    }

    @Override
    public void put(Object key, Object value) {
        CircuitBreaker
                .decorateRunnable(circuitBreaker, () -> redisCache.put(key, value))
                .run();
    }

    @Override
    public void evict(Object key) {
        CircuitBreaker
                .decorateRunnable(circuitBreaker, () -> redisCache.evict(key))
                .run();
    }

    @Override
    public void clear() {
        CircuitBreaker
                .decorateRunnable(circuitBreaker, redisCache::clear)
                .run();
    }
}
