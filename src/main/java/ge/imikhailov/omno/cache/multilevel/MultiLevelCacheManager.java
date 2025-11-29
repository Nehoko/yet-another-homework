package ge.imikhailov.omno.cache.multilevel;

import ge.imikhailov.omno.cache.pubsub.CacheInvalidationPublisher;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class MultiLevelCacheManager implements CacheManager {

    private final CacheManager l1;
    private final CacheManager l2;
    private final @Nullable CacheInvalidationPublisher publisher;
    private final Duration softTtl;

    @Override
    public @Nullable Cache getCache(String name) {
        Cache c1 = l1.getCache(name);
        Cache c2 = l2.getCache(name);

        if (c1 != null && c2 != null) {
            return new MultiLevelCache(name, c1, c2, publisher, softTtl);
        }
        // If only one level has the cache defined, return it as-is
        return c1 != null ? c1 : c2;
    }

    @Override
    public Collection<String> getCacheNames() {
        Set<String> names = new HashSet<>();
        names.addAll(l1.getCacheNames());
        names.addAll(l2.getCacheNames());
        return names;
    }
}
