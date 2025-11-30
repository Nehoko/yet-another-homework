package ge.imikhailov.omno.cache.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

import static org.mockito.Mockito.*;

class RedisCacheInvalidationPublisherTest {

    private StringRedisTemplate redisTemplate;
    private ChannelTopic topic;
    private RedisCacheInvalidationPublisher publisher;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        topic = new ChannelTopic("invalidate");
        publisher = new RedisCacheInvalidationPublisher(redisTemplate, topic);
    }

    @Test
    void publishesEvictWhenArgsPresent() {
        publisher.publishEvict("price", 123);
        verify(redisTemplate).convertAndSend(topic.getTopic(), "EVICT price 123");
    }

    @Test
    void publishEvictIgnoredWhenArgsMissing() {
        publisher.publishEvict(null, 123);
        publisher.publishEvict("price", null);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void publishesClearWhenCacheNamePresent() {
        publisher.publishClear("price");
        verify(redisTemplate).convertAndSend(topic.getTopic(), "CLEAR price");
    }

    @Test
    void publishClearIgnoredWhenCacheNameMissing() {
        publisher.publishClear(null);
        verifyNoInteractions(redisTemplate);
    }
}
