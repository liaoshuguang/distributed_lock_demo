package org.liao.distributed_lock_demo.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 分布式锁
 */
@Slf4j
@Component
public class RedissonDistributedLock {

    /**
     * 空函数
     */
    static Runnable DO_NOTHING = () -> {
    };

    /**
     * 空异常处理
     */
    static Consumer<Exception> BLACK_HOLE = ex -> {
    };

    /**
     * 默认锁时间
     */
    static long DEFAULT_DURATION_MILLS = 10000;

    static RedissonClient redissonClient;

    @Autowired
    public void setRedissonClient(RedissonClient redissonClient) {
        RedissonDistributedLock.redissonClient = redissonClient;
    }

    public static class Builder {

        /**
         * 私有构造
         */
        private Builder() {
        }

        /**
         * 锁键
         */
        String key;

        /**
         * 锁等待时间
         */
        long waitTime = 0;

        /**
         * 锁持续时间
         */
        long duration = DEFAULT_DURATION_MILLS;

        /**
         * 获取锁失败处理函数
         */
        Runnable acquireLockFail = DO_NOTHING;

        /**
         * 执行逻辑发生异常处理函数
         */
        Consumer<? super Exception> exceptionCaught = BLACK_HOLE;

        /**
         * 设置持有锁的时长
         * @param duration 持有锁时长, 单位秒
         * @return Builder
         */
        public Builder duration(long duration) {
            if (duration <= 0) {
                throw new IllegalArgumentException("duration must be greater than 0");
            }
            this.duration = duration;
            return this;
        }

        /**
         * 等待获取锁的时长
         * @param waitTime 等待锁的时间 单位秒
         * @return Builder
         */
        public Builder waitTime(long waitTime) {
            if (waitTime < 0) {
                throw new IllegalArgumentException("waitTime must be greater than 0");
            }
            this.waitTime = waitTime;
            return this;
        }

        /**
         * 获取锁失败后执行函数
         *
         * @param afterFail 获取锁失败后执行函数
         * @return builder
         */
        public Builder lockFailed(Runnable afterFail) {
            this.acquireLockFail = afterFail;
            return this;
        }

        /**
         * 执行逻辑时发生异常执行函数
         *
         * @param exceptionCaught 执行逻辑时发生异常执行函数
         * @return builder
         */
        public Builder exceptionCaught(Consumer<? super Exception> exceptionCaught) {
            this.exceptionCaught = exceptionCaught;
            return this;
        }

        /**
         * 执行业务逻辑并返回执行结果
         *
         * @param callable 业务逻辑
         * @param <T>      返回值类型
         * @return 返回值
         */
        public <T> T supplySyncBlock(Callable<T> callable) {
            if (callable == null) {
                throw new IllegalArgumentException("callable must not be null");
            }
            return executeSyncBlock(key, waitTime, duration, callable, acquireLockFail, exceptionCaught);
        }

        /**
         * 执行业务逻辑
         *
         * @param runnable 业务逻辑
         */
        public void runSyncBlock(Runnable runnable) {
            Callable<Void> callable = () -> {
                runnable.run();
                return null;
            };
            supplySyncBlock(callable);
        }
    }

    /**
     * 创建分布式锁
     *
     * @param key 锁键
     * @return 锁构造器
     */
    public static Builder lockKey(String key) {
        Builder builder = new Builder();
        builder.key = key;
        return builder;
    }

    static <T> T executeSyncBlock(String lockKey, long waitTime, long duration, Callable<T> callable, Runnable acquireLockFail, Consumer<? super Exception> exceptionCaught) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, duration, TimeUnit.SECONDS)) {
                try {
                    log.error("lock success! key: {}", lockKey);
                    return callable.call();
                } catch (Exception ex) {
                    log.error("error encountered! lockKey: {}, err: {}", lockKey, ex.getMessage(), ex);
                    if (exceptionCaught != null) {
                        exceptionCaught.accept(ex);
                    }
                    return null;
                } finally {
                    lock.unlock();
                    log.info("lease distributed lock lockKey: {}", lockKey);
                }
            }
            log.error("try lock failed! key: {}", lockKey);
            if (acquireLockFail != null) {
                acquireLockFail.run();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
