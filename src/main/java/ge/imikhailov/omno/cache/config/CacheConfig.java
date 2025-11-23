package ge.imikhailov.omno.cache.config;

import ge.imikhailov.omno.cache.CacheInvalidationPublisher;
import ge.imikhailov.omno.cache.MultiLevelCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${cache.l1.maximum-size:10000}")
    private long l1MaximumSize;

    @Value("${cache.l1.ttl:10m}")
    private Duration l1Ttl;

    @Value("${cache.l2.ttl:1h}")
    private Duration l2Ttl;

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .maximumSize(l1MaximumSize)
                .expireAfterWrite(l1Ttl.toMillis(), TimeUnit.MILLISECONDS)
                .recordStats();
    }

    @Bean
    public CaffeineCacheManager caffeineCacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(caffeine);
        return manager;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration config = RedisCacheConfiguration
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
    @Primary
    public CacheManager multiLevelCacheManager(CaffeineCacheManager caffeineCacheManager,
                                               RedisCacheManager redisCacheManager,
                                               ObjectProvider<CacheInvalidationPublisher> publisherProvider) {
        return new MultiLevelCacheManager(caffeineCacheManager, redisCacheManager, publisherProvider.getIfAvailable());
    }
}
