package ge.imikhailov.omno.cache;

/**
 * Publishes cache invalidation events to a shared medium (e.g., Redis Pub/Sub).
 * Implementations should be lightweight and non-blocking.
 */
public interface CacheInvalidationPublisher {

    /**
     * Publish an eviction event for a particular cache/key.
     * Format expected by current subscriber: "EVICT <cacheName> <key>"
     */
    void publishEvict(String cacheName, Object key);

    /**
     * Publish a clear event for a particular cache.
     * Format expected by current subscriber: "CLEAR <cacheName>"
     */
    void publishClear(String cacheName);

    /**
     * Publish a clear-all event (optional helper).
     * Format expected by current subscriber: "CLEAR_ALL"
     */
    default void publishClearAll() { /* optional */ }
}
