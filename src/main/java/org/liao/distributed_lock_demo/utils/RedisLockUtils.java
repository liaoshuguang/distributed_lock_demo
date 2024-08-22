package org.liao.distributed_lock_demo.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

@Slf4j
@Component
public class RedisLockUtils {

    private static StringRedisTemplate stringRedisTemplate;

//    static RedissonClient redisson;
//
//    @Autowired
//    public void setRedisson(RedissonClient redisson) {
//        RedisLockUtils.redisson = redisson;
//    }

    @Autowired
    @Qualifier("stringRedisTemplate")
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        RedisLockUtils.stringRedisTemplate = stringRedisTemplate;
    }

    public static <T> T executeSyncBlock(String lockKey, String lockVal, long duration, Callable<T> callable, Runnable acquireLockFail, Consumer<? super Exception> exceptionCaught) {
        try {
            if (!tryLock(lockKey, lockVal, duration)) {
                log.error("try lock failed! key: {}", lockKey);
                if (acquireLockFail != null) {
                    acquireLockFail.run();
                }
                return null;
            }
            log.error("lock success! key: {}", lockKey);
            return callable.call();
        } catch (Exception ex) {
            log.error("error encountered! lockKey: {}, err: {}", lockKey, ex.getMessage(), ex);
            if (exceptionCaught != null) {
                exceptionCaught.accept(ex);
            }
            return null;
        } finally {
            boolean unlock = unlock(lockKey, lockVal);
            log.info("lease distributed lock lockKey: {}, result: {}", lockKey, unlock);
        }
    }

    /**
     * 分布式同步方法块
     *
     * @param lockKey  锁
     * @param duration 锁持续时长
     * @param callable 执行代码块
     * @param e        获取不到锁抛出异常
     */
    public static <T> T executeSyncBlock(String lockKey, String lockVal, long duration, Callable<T> callable, RuntimeException e) {
        try {
            boolean isLock = tryLock(lockKey, lockVal, duration);
            if (!isLock) {
                throw e;
            }
            return callable.call();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            unlock(lockKey, lockVal);
        }
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey   锁
     * @param requestId 请求标识
     * @param duration  超期时间
     * @return 是否获取成功
     */
    public static boolean tryLock(String lockKey, String requestId, long duration) {
        return setIfAbsent(lockKey, requestId, duration);
    }

    /**
     * 上锁
     *
     * @param key
     * @param value
     * @param duration
     * @return 是否成功
     */
    @SuppressWarnings({"unchecked"})
    public static boolean setIfAbsent(final String key, final String value, final long duration) {
        Boolean result = stringRedisTemplate.execute((RedisCallback<Boolean>) connection -> {
            RedisSerializer<String> valueSerializer = (RedisSerializer<String>) stringRedisTemplate.getValueSerializer();
            RedisSerializer<String> keySerializer = (RedisSerializer<String>) stringRedisTemplate.getKeySerializer();
            Object obj = connection.execute("set",
                    keySerializer.serialize(key),
                    valueSerializer.serialize(value),
                    "NX".getBytes(StandardCharsets.UTF_8),
                    "EX".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(duration).getBytes(StandardCharsets.UTF_8));
            return obj != null;
        });
        return Boolean.TRUE.equals(result);
    }

    /**
     * 解锁
     *
     * @param lockKey
     * @param value
     * @return 解锁结果
     */
    public static boolean unlock(String lockKey, String value) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        RedisScript<Boolean> redisScript = new DefaultRedisScript<>(script, Boolean.class);
        Boolean result = stringRedisTemplate.execute(
                redisScript,
                new StringRedisSerializer(),
                new RedisSerializer<>() {
                    @Override
                    public byte[] serialize(Boolean value) throws SerializationException {
                        return value.toString().getBytes(StandardCharsets.UTF_8);
                    }

                    @Override
                    public Boolean deserialize(byte[] bytes) throws SerializationException {
                        return Boolean.getBoolean(new String(bytes, StandardCharsets.UTF_8));
                    }
                },
                Collections.singletonList(lockKey),
                value);
        return Optional.ofNullable(result).orElse(false);
    }
}
