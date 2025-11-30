package ge.imikhailov.omno.cache.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMessageSubscriberTest {

    private CaffeineCacheManager cacheManager;
    private RedisMessageSubscriber subscriber;

    @BeforeEach
    void setUp() {
        cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of("price", "other"));
        subscriber = new RedisMessageSubscriber(cacheManager);
    }

    @Test
    void clearsSpecificCache() {
        Cache price = cacheManager.getCache("price");
        price.put("k1", "v1");
        subscriber.handleMessage("CLEAR price");

        assertThat(price.get("k1")).isNull();
        Cache other = cacheManager.getCache("other");
        assertThat(other.get("k1")).isNull(); // untouched
    }

    @Test
    void evictsEntry() {
        Cache price = cacheManager.getCache("price");
        price.put("k1", "v1");
        subscriber.handleMessage("EVICT price k1");

        assertThat(price.get("k1")).isNull();
    }

    @Test
    void clearsAllCaches() {
        cacheManager.getCache("price").put("k1", "v1");
        cacheManager.getCache("other").put("k2", "v2");

        subscriber.handleMessage("CLEAR_ALL");

        assertThat(cacheManager.getCache("price").get("k1")).isNull();
        assertThat(cacheManager.getCache("other").get("k2")).isNull();
    }

    @Test
    void ignoresMalformedMessagesGracefully() {
        Cache price = cacheManager.getCache("price");
        price.put("k1", "v1");

        // Missing key parameter; should not throw and should not evict.
        subscriber.handleMessage("EVICT price");
        assertThat(price.get("k1")).isNotNull();

        // Unknown op; should no-op.
        subscriber.handleMessage("UNKNOWN price k1");
        assertThat(price.get("k1")).isNotNull();

        // Null/empty should be ignored.
        subscriber.handleMessage((String) null);
        subscriber.handleMessage("   ");
        assertThat(price.get("k1")).isNotNull();
    }

    @Test
    void handlesByteMessage() {
        Cache price = cacheManager.getCache("price");
        price.put("k1", "v1");

        subscriber.handleMessage("CLEAR price".getBytes());

        assertThat(price.get("k1")).isNull();
    }
}
