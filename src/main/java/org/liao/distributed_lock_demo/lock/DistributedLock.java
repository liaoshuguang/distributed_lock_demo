package org.liao.distributed_lock_demo.lock;

import lombok.extern.slf4j.Slf4j;
import org.liao.distributed_lock_demo.utils.RedisLockUtils;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * 分布式锁
 */
@Slf4j
public class DistributedLock {

    /**
     * 空函数
     */
    static Runnable DO_NOTHING = () -> {};

    /**
     * 空异常处理
     */
    static Consumer<Exception> BLACK_HOLE = ex -> {};

    /**
     * 默认锁时间
     */
    static long DEFAULT_DURATION_MILLS = 10000;

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
         * 锁值
         */
        String value = UUID.randomUUID().toString();

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
         * 锁定的值
         * @param value 锁值
         * @return builder
         */
        public Builder lockVal(String value) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("lock value must not be empty");
            }
            this.value = value;
            return this;
        }

        public Builder duration(long duration) {
            if (duration <= 0) {
                throw new IllegalArgumentException("duration must be greater than 0");
            }
            this.duration = duration;
            return this;
        }

        /**
         * 获取锁失败后执行函数
         * @param afterFail 获取锁失败后执行函数
         * @return builder
         */
        public Builder lockFailed(Runnable afterFail) {
            this.acquireLockFail = afterFail;
            return this;
        }

        /**
         * 执行逻辑时发生异常执行函数
         * @param exceptionCaught 执行逻辑时发生异常执行函数
         * @return builder
         */
        public Builder exceptionCaught(Consumer<? super Exception> exceptionCaught) {
            this.exceptionCaught = exceptionCaught;
            return this;
        }

        /**
         * 执行业务逻辑并返回执行结果
         * @param callable 业务逻辑
         * @return 返回值
         * @param <T> 返回值类型
         */
        public <T> T supplySyncBlock(Callable<T> callable) {
            if (callable == null) {
                throw new IllegalArgumentException("callable must not be null");
            }
            return RedisLockUtils.executeSyncBlock(key, value, duration, callable, acquireLockFail, exceptionCaught);
        }

        /**
         * 执行业务逻辑
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
     * @param key 锁键
     * @return 锁构造器
     */
    public static Builder lockKey(String key) {
        Builder builder = new Builder();
        builder.key = key;
        return builder;
    }
}
