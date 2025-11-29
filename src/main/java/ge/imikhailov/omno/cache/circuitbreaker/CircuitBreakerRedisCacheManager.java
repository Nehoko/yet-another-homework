package ge.imikhailov.omno.cache.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Collection;

@RequiredArgsConstructor
public class CircuitBreakerRedisCacheManager implements CacheManager {

    private final RedisCacheManager delegate;
    private final CircuitBreaker circuitBreaker;


    @Override
    public Cache getCache(String name) {
        final Cache cache = delegate.getCache(name);
        return new CircuitBreakerRedisCache(cache, circuitBreaker);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
