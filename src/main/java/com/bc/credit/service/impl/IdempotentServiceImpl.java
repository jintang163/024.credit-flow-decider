package com.bc.credit.service.impl;

import com.bc.credit.service.IdempotentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class IdempotentServiceImpl implements IdempotentService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${credit.application.idempotent-enabled:true}")
    private boolean idempotentEnabled;

    @Value("${credit.application.idempotent-expire-seconds:300}")
    private long defaultExpireSeconds;

    private final Map<String, IdempotentEntry> localCache = new ConcurrentHashMap<>();

    private static final String PROCESSING_SUFFIX = ":processing";
    private static final String RESPONSE_SUFFIX = ":response";

    @Override
    public boolean checkAndAcquire(String key, long expireSeconds) {
        if (!idempotentEnabled) {
            return true;
        }

        long expire = expireSeconds > 0 ? expireSeconds : defaultExpireSeconds;

        try {
            if (stringRedisTemplate != null) {
                String processingKey = key + PROCESSING_SUFFIX;
                Boolean acquired = stringRedisTemplate.opsForValue()
                        .setIfAbsent(processingKey, "1", expire, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(acquired)) {
                    log.debug("幂等锁获取成功(redis): {}", key);
                    return true;
                }

                String existingResponse = stringRedisTemplate.opsForValue().get(key + RESPONSE_SUFFIX);
                if (existingResponse != null) {
                    log.debug("检测到重复请求(redis)，已存在响应: {}", key);
                    return false;
                }

                String processing = stringRedisTemplate.opsForValue().get(processingKey);
                if (processing != null) {
                    log.debug("检测到重复请求(redis)，正在处理中: {}", key);
                    return false;
                }

                acquired = stringRedisTemplate.opsForValue()
                        .setIfAbsent(processingKey, "1", expire, TimeUnit.SECONDS);
                return Boolean.TRUE.equals(acquired);
            } else {
                IdempotentEntry entry = localCache.get(key);
                long now = System.currentTimeMillis();

                if (entry != null) {
                    if (entry.response != null) {
                        log.debug("检测到重复请求(local)，已存在响应: {}", key);
                        return false;
                    }
                    if (entry.processing && (now - entry.createTime) < expire * 1000) {
                        log.debug("检测到重复请求(local)，正在处理中: {}", key);
                        return false;
                    }
                }

                localCache.put(key, new IdempotentEntry(true, null, now));
                log.debug("幂等锁获取成功(local): {}", key);
                return true;
            }
        } catch (Exception e) {
            log.warn("幂等检查异常，默认允许通过: {}", key, e);
            return true;
        }
    }

    @Override
    public void release(String key) {
        if (!idempotentEnabled) {
            return;
        }

        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.delete(key + PROCESSING_SUFFIX);
            } else {
                localCache.remove(key);
            }
            log.debug("幂等锁已释放: {}", key);
        } catch (Exception e) {
            log.warn("释放幂等锁异常: {}", key, e);
        }
    }

    @Override
    public String getExistingResponse(String key) {
        if (!idempotentEnabled) {
            return null;
        }

        try {
            if (stringRedisTemplate != null) {
                return stringRedisTemplate.opsForValue().get(key + RESPONSE_SUFFIX);
            } else {
                IdempotentEntry entry = localCache.get(key);
                return entry != null ? entry.response : null;
            }
        } catch (Exception e) {
            log.warn("获取幂等响应异常: {}", key, e);
            return null;
        }
    }

    @Override
    public void saveResponse(String key, String response, long expireSeconds) {
        if (!idempotentEnabled) {
            return;
        }

        long expire = expireSeconds > 0 ? expireSeconds : defaultExpireSeconds;

        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.opsForValue()
                        .set(key + RESPONSE_SUFFIX, response, expire, TimeUnit.SECONDS);
                stringRedisTemplate.delete(key + PROCESSING_SUFFIX);
            } else {
                long now = System.currentTimeMillis();
                localCache.put(key, new IdempotentEntry(false, response, now));
            }
            log.debug("幂等响应已保存: {}", key);
        } catch (Exception e) {
            log.warn("保存幂等响应异常: {}", key, e);
        }
    }

    @Override
    public boolean isProcessing(String key) {
        if (!idempotentEnabled) {
            return false;
        }

        try {
            if (stringRedisTemplate != null) {
                String processing = stringRedisTemplate.opsForValue().get(key + PROCESSING_SUFFIX);
                return processing != null;
            } else {
                IdempotentEntry entry = localCache.get(key);
                return entry != null && entry.processing;
            }
        } catch (Exception e) {
            log.warn("检查处理状态异常: {}", key, e);
            return false;
        }
    }

    public void cleanExpiredEntries() {
        if (stringRedisTemplate == null) {
            long now = System.currentTimeMillis();
            long expireMs = defaultExpireSeconds * 1000;
            localCache.entrySet().removeIf(entry -> {
                boolean expired = (now - entry.getValue().createTime) > expireMs * 2;
                if (expired) {
                    log.debug("清理过期幂等条目: {}", entry.getKey());
                }
                return expired;
            });
        }
    }

    private static class IdempotentEntry {
        boolean processing;
        String response;
        long createTime;

        IdempotentEntry(boolean processing, String response, long createTime) {
            this.processing = processing;
            this.response = response;
            this.createTime = createTime;
        }
    }
}
