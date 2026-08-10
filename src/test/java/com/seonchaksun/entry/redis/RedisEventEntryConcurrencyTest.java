package com.seonchaksun.entry.redis;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
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
class RedisEventEntryConcurrencyTest {

    private static final int CAPACITY = 100;

    private static final int REQUEST_COUNT = 20;

    private static final long USER_ID = 1L;

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
            "Redis 전략에서도 같은 사용자가 동시에 여러 번 신청하면 한 번만 성공한다"
    )
    void duplicateConcurrentEntry()
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
                                "Redis 중복 신청 동시성 테스트",
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
                        REQUEST_COUNT
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

        AtomicInteger duplicateCount =
                new AtomicInteger();

        AtomicInteger unexpectedFailureCount =
                new AtomicInteger();

        // when
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

                            redisEventEntryService
                                    .enter(
                                            eventId,
                                            USER_ID
                                    );

                            successCount
                                    .incrementAndGet();

                        } catch (
                                DuplicateEntryException e
                        ) {

                            duplicateCount
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

        startLatch.countDown();

        boolean completed =
                doneLatch.await(
                        30,
                        TimeUnit.SECONDS
                );

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

        long actualEntryCount =
                eventEntryRepository
                        .countByEventId(
                                eventId
                        );

        long redisCount =
                redisCapacityService
                        .getCurrentCount(
                                eventId
                        );

        System.out.println(
                "===================================="
        );

        System.out.println(
                "Redis + MySQL 중복 신청 테스트 결과"
        );

        System.out.println(
                "------------------------------------"
        );

        System.out.println(
                "동일 사용자 요청 수 = "
                        + REQUEST_COUNT
        );

        System.out.println(
                "성공 수 = "
                        + successCount.get()
        );

        System.out.println(
                "중복 신청 실패 수 = "
                        + duplicateCount.get()
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
                "===================================="
        );

        assertThat(
                successCount.get()
        )
                .isEqualTo(1);

        assertThat(
                duplicateCount.get()
        )
                .isEqualTo(
                        REQUEST_COUNT - 1
                );

        assertThat(
                unexpectedFailureCount.get()
        )
                .isZero();

        assertThat(
                redisCount
        )
                .isEqualTo(1);

        assertThat(
                actualEntryCount
        )
                .isEqualTo(1);

        assertThat(
                redisCount
        )
                .isEqualTo(
                        actualEntryCount
                );
    }
}