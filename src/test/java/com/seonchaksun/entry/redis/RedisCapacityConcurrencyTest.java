package com.seonchaksun.entry.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisCapacityConcurrencyTest {

    private static final Long EVENT_ID = 1000L;

    private static final int CAPACITY = 100;

    private static final int REQUEST_COUNT = 200;

    private static final int THREAD_COUNT = 32;

    @Autowired
    private RedisCapacityService redisCapacityService;

    @AfterEach
    void tearDown() {
        redisCapacityService.clear(EVENT_ID);
    }

    @Test
    @DisplayName(
            "Redis Lua Script로 동시 요청에서도 정원까지만 예약된다"
    )
    void reserveConcurrently() throws Exception {

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        /*
         * 실제로 동시에 실행 가능한 worker thread 수만큼
         * 준비 완료를 기다린다.
         */
        CountDownLatch readyLatch =
                new CountDownLatch(
                        THREAD_COUNT
                );

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(
                        REQUEST_COUNT
                );

        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger failureCount =
                new AtomicInteger();

        for (
                int i = 0;
                i < REQUEST_COUNT;
                i++
        ) {
            executorService.submit(
                    () -> {

                        readyLatch.countDown();

                        try {
                            startLatch.await();

                            boolean success =
                                    redisCapacityService
                                            .reserve(
                                                    EVENT_ID,
                                                    CAPACITY
                                            );

                            if (success) {
                                successCount
                                        .incrementAndGet();
                            } else {
                                failureCount
                                        .incrementAndGet();
                            }

                        } catch (
                                Exception e
                        ) {
                            failureCount
                                    .incrementAndGet();

                        } finally {
                            doneLatch.countDown();
                        }
                    }
            );
        }

        boolean ready =
                readyLatch.await(
                        5,
                        TimeUnit.SECONDS
                );

        assertThat(ready)
                .isTrue();

        long startTime =
                System.nanoTime();

        startLatch.countDown();

        boolean completed =
                doneLatch.await(
                        30,
                        TimeUnit.SECONDS
                );

        long endTime =
                System.nanoTime();

        executorService.shutdown();

        if (!executorService.awaitTermination(
                5,
                TimeUnit.SECONDS
        )) {
            executorService.shutdownNow();
        }

        assertThat(completed)
                .isTrue();

        long elapsedMillis =
                TimeUnit.NANOSECONDS
                        .toMillis(
                                endTime - startTime
                        );

        long redisCount =
                redisCapacityService
                        .getCurrentCount(
                                EVENT_ID
                        );

        System.out.println(
                "===================================="
        );

        System.out.println(
                "Redis 동시성 테스트 결과"
        );

        System.out.println(
                "------------------------------------"
        );

        System.out.println(
                "요청 수 = "
                        + REQUEST_COUNT
        );

        System.out.println(
                "정원 = "
                        + CAPACITY
        );

        System.out.println(
                "Thread 수 = "
                        + THREAD_COUNT
        );

        System.out.println(
                "성공 수 = "
                        + successCount.get()
        );

        System.out.println(
                "실패 수 = "
                        + failureCount.get()
        );

        System.out.println(
                "Redis count = "
                        + redisCount
        );

        System.out.println(
                "처리 시간 = "
                        + elapsedMillis
                        + " ms"
        );

        System.out.println(
                "===================================="
        );

        assertThat(
                successCount.get()
        )
                .isEqualTo(
                        CAPACITY
                );

        assertThat(
                failureCount.get()
        )
                .isEqualTo(
                        REQUEST_COUNT - CAPACITY
                );

        assertThat(
                redisCount
        )
                .isEqualTo(
                        CAPACITY
                );
    }
}