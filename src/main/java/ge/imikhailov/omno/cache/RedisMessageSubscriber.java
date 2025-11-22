package ge.imikhailov.omno.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Subscribes to a Redis Pub/Sub channel and invalidates the local L1 (Caffeine) cache accordingly.
 * </br>
 * Supported message formats (space-delimited):
 * - CLEAR <cacheName>
 * - EVICT <cacheName> <key>
 * - CLEAR_ALL
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    private final CaffeineCacheManager caffeineCacheManager;

    // Called by MessageListenerAdapter via reflection
    public void handleMessage(String message) {
        if (message == null) return;
        try {
            processMessage(message.trim());
        } catch (Exception e) {
            log.warn("Failed to process cache-invalidate message: {}", message, e);
        }
    }

    // Overload in case the adapter passes byte[]
    public void handleMessage(byte[] bytes) {
        if (bytes == null) return;
        handleMessage(new String(bytes, StandardCharsets.UTF_8));
    }

    private void processMessage(String msg) {
        if (msg.isEmpty()) return;
        String[] parts = msg.split("\\s+");
        String op = parts[0].toUpperCase(Locale.ROOT);

        switch (op) {
            case "CLEAR_ALL" -> clearAll();
            case "CLEAR" -> {
                if (parts.length < 2) {
                    log.warn("CLEAR requires <cacheName>: {}", msg);
                    return;
                }
                clear(parts[1]);
            }
            case "EVICT" -> {
                if (parts.length < 3) {
                    log.warn("EVICT requires <cacheName> <key>: {}", msg);
                    return;
                }
                evict(parts[1], parts[2]);
            }
            default -> log.warn("Unknown cache-invalidate operation: {}", op);
        }
    }

    private void clearAll() {
        for (String name : caffeineCacheManager.getCacheNames()) {
            Cache cache = caffeineCacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
        log.debug("Cleared all L1 caches");
    }

    private void clear(String cacheName) {
        Cache cache = caffeineCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared L1 cache: {}", cacheName);
        } else {
            log.debug("L1 cache not found to clear: {}", cacheName);
        }
    }

    private void evict(String cacheName, Object key) {
        Cache cache = caffeineCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("Evicted key from L1 cache: {} -> {}", cacheName, key);
        } else {
            log.debug("L1 cache not found to evict from: {}", cacheName);
        }
    }
}
