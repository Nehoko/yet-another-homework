package ge.imikhailov.omno.cache.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CircuitBreakerRedisCacheManagerTest {

    @Test
    void wrapsDelegateCache() {
        RedisCacheManager delegate = mock(RedisCacheManager.class);
        Cache rawCache = mock(Cache.class);
        when(delegate.getCache("price")).thenReturn(rawCache);
        when(delegate.getCacheNames()).thenReturn(Set.of("price"));

        CircuitBreakerRedisCacheManager mgr = new CircuitBreakerRedisCacheManager(delegate, CircuitBreaker.ofDefaults("cb"));

        Cache wrapped = mgr.getCache("price");
        assertThat(wrapped).isInstanceOf(CircuitBreakerRedisCache.class);
        assertThat(mgr.getCacheNames()).containsExactly("price");
    }
}
