package ge.imikhailov.omno.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import ge.imikhailov.omno.cache.circuitbreaker.CircuitBreakerRedisCacheManager;
import ge.imikhailov.omno.cache.multilevel.MultiLevelCacheManager;
import ge.imikhailov.omno.cache.pubsub.CacheInvalidationPublisher;
import ge.imikhailov.omno.metrics.CacheMetricsFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.l1.maximum-size:10000}")
    private long l1MaximumSize;

    @Value("${cache.l1.ttl:10m}")
    private Duration l1Ttl;

    @Value("${cache.l2.ttl:1h}")
    private Duration l2Ttl;

    @Bean
    @ConditionalOnProperty(prefix = "cache.l1", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(l1MaximumSize)
                .expireAfterWrite(l1Ttl.toMillis(), TimeUnit.MILLISECONDS)
                .recordStats();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cache.l1", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CaffeineCacheManager caffeineCacheManager(final Caffeine<Object, Object> caffeine) {
        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(caffeine);
        return manager;
    }

    @Bean
    @ConditionalOnProperty(prefix = "cache.l2", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisCacheManager redisCacheManager(final RedisConnectionFactory connectionFactory) {

        final RedisCacheConfiguration config = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(l2Ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()));

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cache.l2", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CircuitBreakerRedisCacheManager circuitBreakerRedisCache(final RedisCacheManager redisCacheManager,
                                                                    final CircuitBreaker cacheL2CircuitBreaker) {
        return new CircuitBreakerRedisCacheManager(redisCacheManager, cacheL2CircuitBreaker);
    }

    @Bean
    @ConditionalOnProperty(prefix = "cache.l2", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CircuitBreaker cacheL2CircuitBreaker(final CircuitBreakerRegistry registry) {
        // Retrieve or create a circuit breaker named "cacheL2" configured via application properties
        return registry.circuitBreaker("cacheL2");
    }

    @Bean
    @Primary
    public CacheManager primaryCacheManager(final ObjectProvider<CaffeineCacheManager> caffeineCacheManager,
                                            final ObjectProvider<CircuitBreakerRedisCacheManager> circuitBreakerRedisCacheManager,
                                            final ObjectProvider<CacheInvalidationPublisher> publisherProvider,
                                            final CacheMetricsFactory cacheMetricsFactory) {
        if (!cacheEnabled) {
            return new NoOpCacheManager();
        }

        final CaffeineCacheManager l1 = caffeineCacheManager.getIfAvailable();
        final CircuitBreakerRedisCacheManager l2 = circuitBreakerRedisCacheManager.getIfAvailable();

        if (l1 != null && l2 != null) {
            // Soft TTL for stale-while-revalidate: refresh slightly before L1 TTL expires.
            final Duration softTtl = l1Ttl.isZero() ? Duration.ZERO : Duration.ofMillis(Math.max(1L, (long) (l1Ttl.toMillis() * 0.8)));
            return new MultiLevelCacheManager(l1, l2, publisherProvider.getIfAvailable(), softTtl, cacheMetricsFactory);
        }
        if (l1 != null) return l1;
        if (l2 != null) return l2;
        return new NoOpCacheManager();
    }
}
