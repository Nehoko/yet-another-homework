package ge.imikhailov.omno.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

@Slf4j
@RequiredArgsConstructor
public class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic topic;

    @Override
    public void publishEvict(String cacheName, Object key) {
        if (cacheName == null || key == null) return;
        String payload = "EVICT " + cacheName + " " + key;
        try {
            redisTemplate.convertAndSend(topic.getTopic(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish cache evict: {}", payload, e);
        }
    }

    @Override
    public void publishClear(String cacheName) {
        if (cacheName == null) return;
        String payload = "CLEAR " + cacheName;
        try {
            redisTemplate.convertAndSend(topic.getTopic(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish cache clear: {}", payload, e);
        }
    }
}
