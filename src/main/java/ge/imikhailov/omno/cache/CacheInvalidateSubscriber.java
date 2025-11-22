package ge.imikhailov.omno.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "cache.invalidate", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CacheInvalidateSubscriber {

    @Bean
    ChannelTopic topic() {
        return new ChannelTopic("cache-invalidate");
    }

    @Bean
    RedisMessageSubscriber redisMessageSubscriber(CaffeineCacheManager caffeineCacheManager) {
        return new RedisMessageSubscriber(caffeineCacheManager);
    }

    @Bean
    MessageListenerAdapter messageListener(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "handleMessage");
    }

    @Bean
    RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                 MessageListenerAdapter listenerAdapter,
                                                 ChannelTopic topic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, topic);
        return container;
    }

    @Bean
    RedisCacheInvalidationPublisher cacheInvalidationPublisher(StringRedisTemplate stringRedisTemplate,
                                                              ChannelTopic topic) {
        return new RedisCacheInvalidationPublisher(stringRedisTemplate, topic);
    }
}
