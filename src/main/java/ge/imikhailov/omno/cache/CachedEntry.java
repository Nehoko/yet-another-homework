package ge.imikhailov.omno.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper stored in caches to support soft TTL and stale-while-revalidate.
 * Serialized to Redis via JSON serializer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedEntry {
    private Object value;
    private long writeTimeMs;
}
