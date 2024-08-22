package org.liao.distributed_lock_demo;

import lombok.extern.slf4j.Slf4j;
import org.liao.distributed_lock_demo.lock.DistributedLock;
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
    public void run(ApplicationArguments args) {
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
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("任务结束啦");
                });

    }
}
