package luv.vansye.sync.lock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SyncLock {

    private static final String KEY = "obsidian-blog:sync:lock";

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SyncLock(StringRedisTemplate redis, @Value("${blog.sync.lock-ttl}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public Optional<String> tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(KEY, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public boolean release(String token) {
        Long deleted = redis.execute(RELEASE, List.of(KEY), token);
        return deleted != null && deleted == 1L;
    }
}
