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
    @DisplayName("동시에 많은 사용자가 신청하면 Naive 구현에서 데이터 정합성이 깨진다")
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
                                2026, 8, 10, 10, 0
                        ),
                        LocalDateTime.of(
                                2026, 8, 10, 18, 0
                        )
                )
        );

        Long eventId = event.getId();

        ExecutorService executorService =
                Executors.newFixedThreadPool(32);

        CountDownLatch readyLatch =
                new CountDownLatch(REQUEST_COUNT);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(REQUEST_COUNT);

        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger failureCount =
                new AtomicInteger();

        // when
        for (int i = 0; i < REQUEST_COUNT; i++) {
            long userId = i + 1L;

            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();

                    eventEntryService.enter(
                            eventId,
                            userId
                    );

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();

                } finally {
                    doneLatch.countDown();
                }
            });
        }

        /*
         * 모든 요청을 최대한 동시에 출발시키기 위한 장치.
         *
         * 스레드 풀은 32개이므로 REQUEST_COUNT 전체가
         * 동시에 실행될 수는 없다.
         *
         * 그래서 readyLatch를 너무 오래 기다리지 않고
         * 일정 시간이 지나면 현재 준비된 스레드를
         * 한꺼번에 출발시킨다.
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

        assertThat(completed).isTrue();

        entityManager.clear();

        // then
        Event foundEvent =
                eventRepository
                        .findById(eventId)
                        .orElseThrow();

        long actualEntryCount =
                eventEntryRepository
                        .countByEventId(eventId);

        System.out.println(
                "===================================="
        );

        System.out.println(
                "요청 수 = " + REQUEST_COUNT
        );

        System.out.println(
                "정원 = " + CAPACITY
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
         * 지금 단계의 목적은
         * "정상 동작을 검증"하는 것이 아니라
         * Race Condition을 관찰하는 것이다.
         *
         * 따라서 일부러
         * currentCount == actualEntryCount == 100
         * 같은 정상 조건을 assert하지 않는다.
         */
    }
}