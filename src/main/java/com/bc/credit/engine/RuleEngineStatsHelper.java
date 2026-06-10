package com.bc.credit.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RuleEngineStatsHelper {

    private static final String HIT_COUNT_PREFIX = "rule:stats:hit:";
    private static final String EXEC_COUNT_PREFIX = "rule:stats:exec:";
    private static final String EXEC_TIME_PREFIX = "rule:stats:time:";

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final ConcurrentMap<String, AtomicLong> localHitCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> localExecCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> localExecTimes = new ConcurrentHashMap<>();

    public void recordHit(String module, String ruleCode) {
        String key = module + ":" + ruleCode;
        localHitCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        incrementRedis(HIT_COUNT_PREFIX + key);
    }

    public void recordExecution(String module, String group, int firedCount, long elapsedMs) {
        String execKey = module + ":" + group;
        localExecCounts.computeIfAbsent(execKey, k -> new AtomicLong(0)).addAndGet(firedCount);
        localExecTimes.computeIfAbsent(execKey, k -> new AtomicLong(0)).addAndGet(elapsedMs);
        incrementRedis(EXEC_COUNT_PREFIX + execKey, firedCount);
        incrementRedis(EXEC_TIME_PREFIX + execKey, elapsedMs);
    }

    public long getHitCount(String module, String ruleCode) {
        String key = module + ":" + ruleCode;
        AtomicLong local = localHitCounts.get(key);
        long localVal = local != null ? local.get() : 0;

        if (stringRedisTemplate != null) {
            try {
                String redisVal = stringRedisTemplate.opsForValue().get(HIT_COUNT_PREFIX + key);
                long redisLong = redisVal != null ? Long.parseLong(redisVal) : 0;
                return Math.max(localVal, redisLong);
            } catch (Exception e) {
                log.warn("Failed to get hit count from Redis, key: {}", key, e);
            }
        }
        return localVal;
    }

    public long getExecCount(String module, String group) {
        String key = module + ":" + group;
        AtomicLong local = localExecCounts.get(key);
        long localVal = local != null ? local.get() : 0;

        if (stringRedisTemplate != null) {
            try {
                String redisVal = stringRedisTemplate.opsForValue().get(EXEC_COUNT_PREFIX + key);
                long redisLong = redisVal != null ? Long.parseLong(redisVal) : 0;
                return Math.max(localVal, redisLong);
            } catch (Exception e) {
                log.warn("Failed to get exec count from Redis, key: {}", key, e);
            }
        }
        return localVal;
    }

    public long getAvgExecTimeMs(String module, String group) {
        String key = module + ":" + group;
        AtomicLong timeLocal = localExecTimes.get(key);
        AtomicLong countLocal = localExecCounts.get(key);
        long totalTime = timeLocal != null ? timeLocal.get() : 0;
        long totalCount = countLocal != null ? countLocal.get() : 0;
        if (totalCount == 0) return 0;
        return totalTime / totalCount;
    }

    public void resetStats(String module) {
        String prefix = module + ":";
        localHitCounts.keySet().removeIf(k -> k.startsWith(prefix));
        localExecCounts.keySet().removeIf(k -> k.startsWith(prefix));
        localExecTimes.keySet().removeIf(k -> k.startsWith(prefix));

        if (stringRedisTemplate != null) {
            try {
                Set<String> keys = stringRedisTemplate.keys(
                        HIT_COUNT_PREFIX + prefix + "*");
                if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
                keys = stringRedisTemplate.keys(
                        EXEC_COUNT_PREFIX + prefix + "*");
                if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
                keys = stringRedisTemplate.keys(
                        EXEC_TIME_PREFIX + prefix + "*");
                if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
            } catch (Exception e) {
                log.warn("Failed to reset stats in Redis, module: {}", module, e);
            }
        }
    }

    private void incrementRedis(String key) {
        incrementRedis(key, 1);
    }

    private void incrementRedis(String key, long delta) {
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForValue().increment(key, delta);
            } catch (Exception e) {
                log.warn("Failed to increment Redis key: {}", key, e);
            }
        }
    }
}
