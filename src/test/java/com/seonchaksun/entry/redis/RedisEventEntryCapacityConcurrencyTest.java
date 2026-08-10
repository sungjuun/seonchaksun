package com.seonchaksun.entry.redis;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventException;
import com.seonchaksun.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisEventEntryCapacityConcurrencyTest {

    private static final int CAPACITY = 100;

    private static final int REQUEST_COUNT = 200;

    private static final int THREAD_COUNT = 32;

    @Autowired
    private RedisEventEntryService redisEventEntryService;

    @Autowired
    private RedisCapacityService redisCapacityService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventEntryRepository eventEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    private Long eventId;

    @AfterEach
    void tearDown() {

        if (eventId != null) {
            redisCapacityService.clear(
                    eventId
            );
        }
    }

    @Test
    @DisplayName(
            "Redis와 MySQL을 함께 사용해도 동시 요청에서 정원까지만 신청된다"
    )
    void enterConcurrently()
            throws Exception {

        // given
        when(clock.instant())
                .thenReturn(
                        Instant.parse(
                                "2026-08-10T03:00:00Z"
                        )
                );

        when(clock.getZone())
                .thenReturn(
                        ZoneId.of(
                                "Asia/Seoul"
                        )
                );

        Event event =
                eventRepository.save(
                        Event.create(
                                "Redis 정원 동시성 테스트",
                                CAPACITY,
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        10,
                                        10,
                                        0
                                ),
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        10,
                                        18,
                                        0
                                )
                        )
                );

        eventId =
                event.getId();

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        /*
         * Thread Pool이 32이므로
         * 실제 동시에 시작 가능한 Worker는 최대 32개다.
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

        AtomicInteger capacityFailureCount =
                new AtomicInteger();

        AtomicInteger unexpectedFailureCount =
                new AtomicInteger();

        for (
                int i = 0;
                i < REQUEST_COUNT;
                i++
        ) {

            /*
             * 이번 테스트에서는 모든 사용자가 달라야 한다.
             *
             * 따라서 중복 신청 문제가 아니라
             * 순수하게 정원 제어를 검증한다.
             */
            long userId =
                    i + 1L;

            executorService.submit(
                    () -> {

                        readyLatch.countDown();

                        try {

                            startLatch.await();

                            redisEventEntryService
                                    .enter(
                                            eventId,
                                            userId
                                    );

                            successCount
                                    .incrementAndGet();

                        } catch (
                                EventException e
                        ) {

                            /*
                             * 정원이 100명이므로
                             * Redis 예약에서 탈락한 요청은
                             * 여기로 들어온다.
                             */
                            capacityFailureCount
                                    .incrementAndGet();

                        } catch (
                                Exception e
                        ) {

                            unexpectedFailureCount
                                    .incrementAndGet();

                            e.printStackTrace();

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

        /*
         * 기존 성능 테스트와 측정 기준을 맞춘다.
         *
         * 모든 Worker가 준비된 뒤
         * startLatch를 열기 직전부터 시간을 측정한다.
         */
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

        entityManager.clear();

        long redisCount =
                redisCapacityService
                        .getCurrentCount(
                                eventId
                        );

        long actualEntryCount =
                eventEntryRepository
                        .countByEventId(
                                eventId
                        );

        long elapsedMillis =
                TimeUnit.NANOSECONDS
                        .toMillis(
                                endTime - startTime
                        );

        System.out.println(
                "===================================="
        );

        System.out.println(
                "Redis + MySQL 정원 동시성 테스트 결과"
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
                "최대 동시 Thread 수 = "
                        + THREAD_COUNT
        );

        System.out.println(
                "성공 수 = "
                        + successCount.get()
        );

        System.out.println(
                "정원 초과 실패 수 = "
                        + capacityFailureCount.get()
        );

        System.out.println(
                "예상하지 못한 실패 수 = "
                        + unexpectedFailureCount.get()
        );

        System.out.println(
                "Redis count = "
                        + redisCount
        );

        System.out.println(
                "실제 EventEntry 수 = "
                        + actualEntryCount
        );

        System.out.println(
                "처리 시간 = "
                        + elapsedMillis
                        + " ms"
        );

        System.out.println(
                "===================================="
        );

        // then
        assertThat(
                successCount.get()
        )
                .isEqualTo(
                        CAPACITY
                );

        assertThat(
                capacityFailureCount.get()
        )
                .isEqualTo(
                        REQUEST_COUNT - CAPACITY
                );

        assertThat(
                unexpectedFailureCount.get()
        )
                .isZero();

        assertThat(
                redisCount
        )
                .isEqualTo(
                        CAPACITY
                );

        assertThat(
                actualEntryCount
        )
                .isEqualTo(
                        CAPACITY
                );

        /*
         * Redis가 알고 있는 신청 수와
         * MySQL에 실제 저장된 신청 수가 같아야 한다.
         */
        assertThat(
                redisCount
        )
                .isEqualTo(
                        actualEntryCount
                );
    }
}