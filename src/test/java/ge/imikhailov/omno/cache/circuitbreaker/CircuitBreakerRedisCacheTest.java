package ge.imikhailov.omno.cache.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CircuitBreakerRedisCacheTest {

    private Cache delegate;
    private CircuitBreaker circuitBreaker;
    private CircuitBreakerRedisCache cache;

    @BeforeEach
    void setUp() {
        delegate = mock(Cache.class);
        circuitBreaker = CircuitBreaker.ofDefaults("cb");
        cache = new CircuitBreakerRedisCache(delegate, circuitBreaker);
    }

    @Test
    void forwardsBasicOperationsThroughBreaker() {
        when(delegate.getName()).thenReturn("price");
        assertThat(cache.getName()).isEqualTo("price");

        cache.getNativeCache();
        verify(delegate).getNativeCache();

        cache.clear();
        verify(delegate).clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getWithCallableDelegates() throws Exception {
        Callable<String> loader = mock(Callable.class);
        when(loader.call()).thenReturn("v");
        when(delegate.get(any(), any(Callable.class))).thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(1)).call());

        String result = cache.get("k1", loader);

        assertThat(result).isEqualTo("v");
        verify(loader).call();
    }

    @Test
    void putAndEvictAndGetUseDelegate() {
        AtomicBoolean putCalled = new AtomicBoolean();
        doAnswer(inv -> { putCalled.set(true); return null; }).when(delegate).put("k1", "v1");

        cache.put("k1", "v1");
        assertThat(putCalled).isTrue();

        cache.evict("k1");
        verify(delegate).evict("k1");

        cache.get("k1");
        verify(delegate).get("k1");
    }
}
