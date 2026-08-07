package com.seonchaksun.entry.service;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.domain.DuplicateEntryException;
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
class EventEntryDuplicateConcurrencyTest {

    private static final int CAPACITY = 100;

    /*
     * 동일 사용자가 동시에 20번 신청한다.
     */
    private static final int REQUEST_COUNT = 20;

    private static final long USER_ID = 1L;

    @Autowired
    private EventEntryService eventEntryService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventEntryRepository eventEntryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "같은 사용자가 동시에 여러 번 신청해도 한 번만 성공한다"
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
                                "중복 신청 동시성 테스트",
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

                            eventEntryService.enter(
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

                            /*
                             * DuplicateEntryException 이외의
                             * 예외가 발생했는지도 별도로 확인한다.
                             */
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
         * 20개 요청 동시 시작
         */
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

        /*
         * worker Transaction에서 변경된 DB 상태를
         * 최신 값으로 다시 읽기 위해 clear.
         */
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
                "중복 신청 동시성 테스트 결과"
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
                "Event.currentCount = "
                        + foundEvent.getCurrentCount()
        );

        System.out.println(
                "실제 EventEntry 수 = "
                        + actualEntryCount
        );

        System.out.println(
                "===================================="
        );

        /*
         * 동일 사용자는 정확히 한 번만 성공해야 한다.
         */
        assertThat(
                successCount.get()
        )
                .isEqualTo(1);

        /*
         * 나머지 19번은 모두 중복 신청으로 처리되어야 한다.
         */
        assertThat(
                duplicateCount.get()
        )
                .isEqualTo(
                        REQUEST_COUNT - 1
                );

        /*
         * 중복 외의 예상하지 못한 오류는 없어야 한다.
         */
        assertThat(
                unexpectedFailureCount.get()
        )
                .isZero();

        /*
         * 신청자가 한 명뿐이므로
         * Event.currentCount도 1이어야 한다.
         */
        assertThat(
                foundEvent.getCurrentCount()
        )
                .isEqualTo(1);

        /*
         * 실제 EventEntry 역시 한 건만 존재해야 한다.
         */
        assertThat(
                actualEntryCount
        )
                .isEqualTo(1);

        /*
         * 가장 중요한 정합성 검증.
         */
        assertThat(
                foundEvent.getCurrentCount()
        )
                .isEqualTo(
                        actualEntryCount
                );
    }
}