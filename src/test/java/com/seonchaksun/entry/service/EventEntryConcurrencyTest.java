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
class EventEntryConcurrencyTest {

    private static final int CAPACITY = 100;
    private static final int REQUEST_COUNT = 200;
    private static final int THREAD_COUNT = 32;

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
    @DisplayName("Atomic Update를 사용하면 동시 신청에서도 정원을 초과하지 않는다")
    void concurrentEntry() throws Exception {
        // given
        when(clock.instant())
                .thenReturn(
                        Instant.parse(
                                "2026-08-10T03:00:00Z"
                        )
                );

        when(clock.getZone())
                .thenReturn(
                        ZoneId.of("Asia/Seoul")
                );

        Event event = eventRepository.save(
                Event.create(
                        "동시성 테스트 이벤트",
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

        Long eventId = event.getId();

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
        for (int i = 0; i < REQUEST_COUNT; i++) {
            long userId = i + 1L;

            executorService.submit(() -> {
                /*
                 * 작업 스레드가 준비됐음을 알린다.
                 */
                readyLatch.countDown();

                try {
                    /*
                     * startLatch가 열릴 때까지 기다렸다가
                     * 최대한 동시에 신청 요청을 시작한다.
                     */
                    startLatch.await();

                    eventEntryService.enter(
                            eventId,
                            userId
                    );

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();

                } finally {
                    /*
                     * 성공/실패 여부와 관계없이
                     * 해당 요청이 끝났음을 알린다.
                     */
                    doneLatch.countDown();
                }
            });
        }

        /*
         * THREAD_COUNT가 32이므로
         * 200개 작업 전체가 동시에 ready 상태가 될 수 없다.
         *
         * 따라서 최대 1초까지만 기다린 뒤
         * 현재 준비된 작업들을 동시에 출발시킨다.
         */
        readyLatch.await(
                1,
                TimeUnit.SECONDS
        );

        startLatch.countDown();

        boolean completed =
                doneLatch.await(
                        30,
                        TimeUnit.SECONDS
                );

        executorService.shutdown();

        assertThat(completed)
                .isTrue();

        /*
         * 다른 트랜잭션들이 DB를 수정했으므로
         * 혹시 남아 있을 수 있는 1차 캐시를 비운다.
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
                "Atomic Update 동시성 테스트 결과"
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
         * Atomic Update 적용 후 기대하는 정합성
         *
         * 총 요청       = 200
         * 정원          = 100
         *
         * 성공          = 100
         * 실패          = 100
         *
         * currentCount  = 100
         * entry row     = 100
         */

        assertThat(successCount.get())
                .isEqualTo(CAPACITY);

        assertThat(failureCount.get())
                .isEqualTo(
                        REQUEST_COUNT - CAPACITY
                );

        assertThat(
                foundEvent.getCurrentCount()
        )
                .isEqualTo(CAPACITY);

        assertThat(actualEntryCount)
                .isEqualTo(CAPACITY);

        /*
         * 특히 중요한 정합성 검증.
         *
         * Event에 기록된 신청 인원과
         * 실제 신청 내역 row 수가 반드시 같아야 한다.
         */
        assertThat(
                foundEvent.getCurrentCount()
        )
                .isEqualTo(
                        actualEntryCount
                );
    }
}