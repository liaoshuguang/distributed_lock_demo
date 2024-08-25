package org.liao.distributed_lock_demo;

import lombok.extern.slf4j.Slf4j;
import org.liao.distributed_lock_demo.lock.RedissonDistributedLock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootApplication
public class DistributedLockDemoApplication implements ApplicationRunner {

    public static void main(String[] args) {
        SpringApplication.run(DistributedLockDemoApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
/*
        DistributedLock.lockKey("test")
                .duration(20)
                .lockFailed(() -> {
                    log.warn("获取锁失败了");
                })
                .exceptionCaught(ex -> {
                    log.error("发生异常: {}", ex.getMessage());
                })
                .runSyncBlock(() -> {
                    log.info("任务开始执行了");
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("任务结束啦");
                });
*/

        Thread t1 = new Thread(DistributedLockDemoApplication::extracted);
        Thread t2 = new Thread(DistributedLockDemoApplication::extracted);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    private static void extracted() {
        String str =  RedissonDistributedLock.lockKey("redisson_test")
                .waitTime(2)
                .duration(5)
                .lockFailed(() -> log.info("redisson get lock failed!"))
                .exceptionCaught(ex -> log.error("redisson exception: {}", ex.getMessage()))
                .supplySyncBlock(() -> {
                    log.info("redisson start");
                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("redisson end");
                    return "GOOD";
                });
        log.info(str);
    }
}
