package com.seonchaksun.entry.service;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.repository.EventRepository;
import jakarta.persistence.EntityManager;
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
class OptimisticEventEntryConcurrencyTest {

    private static final int CAPACITY = 100;

    private static final int REQUEST_COUNT = 200;

    private static final int THREAD_COUNT = 32;

    @Autowired
    private OptimisticEventEntryService
            optimisticEventEntryService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventEntryRepository
            eventEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "낙관적 락과 재시도를 사용하면 동시 신청에서도 정원을 초과하지 않는다"
    )
    void concurrentEntry()
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
                                "Optimistic Lock 동시성 테스트",
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

        Long eventId =
                event.getId();

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        CountDownLatch readyLatch =
                new CountDownLatch(
                        REQUEST_COUNT
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

        // when
        for (
                int i = 0;
                i < REQUEST_COUNT;
                i++
        ) {

            long userId =
                    i + 1L;

            executorService.submit(
                    () -> {

                        readyLatch
                                .countDown();

                        try {

                            startLatch
                                    .await();

                            optimisticEventEntryService
                                    .enter(
                                            eventId,
                                            userId
                                    );

                            successCount
                                    .incrementAndGet();

                        } catch (
                                Exception e
                        ) {

                            failureCount
                                    .incrementAndGet();

                        } finally {

                            doneLatch
                                    .countDown();
                        }
                    }
            );
        }

        readyLatch.await(
                1,
                TimeUnit.SECONDS
        );

        /*
         * 다른 전략과 동일하게
         * 실제 요청 시작 직전부터 시간 측정.
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

        assertThat(completed)
                .isTrue();

        long elapsedMillis =
                TimeUnit.NANOSECONDS
                        .toMillis(
                                endTime
                                        - startTime
                        );

        entityManager.clear();

        // then
        Event foundEvent =
                eventRepository
                        .findById(eventId)
                        .orElseThrow();

        long actualEntryCount =
                eventEntryRepository
                        .countByEventId(
                                eventId
                        );

        System.out.println(
                "===================================="
        );

        System.out.println(
                "Optimistic Lock 동시성 테스트 결과"
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
                "서비스 성공 수 = "
                        + successCount.get()
        );

        System.out.println(
                "서비스 실패 수 = "
                        + failureCount.get()
        );

        System.out.println(
                "Event.currentCount = "
                        + foundEvent
                        .getCurrentCount()
        );

        System.out.println(
                "실제 EventEntry 수 = "
                        + actualEntryCount
        );

        System.out.println(
                "Event.version = "
                        + foundEvent
                        .getVersion()
        );

        System.out.println(
                "총 처리 시간(ms) = "
                        + elapsedMillis
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
                        REQUEST_COUNT
                                - CAPACITY
                );

        assertThat(
                foundEvent
                        .getCurrentCount()
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

        assertThat(
                foundEvent
                        .getCurrentCount()
        )
                .isEqualTo(
                        actualEntryCount
                );
    }
}