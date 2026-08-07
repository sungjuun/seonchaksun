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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventEntryStrategyPerformanceTest {

    private static final int CAPACITY = 100;

    private static final int REQUEST_COUNT = 200;

    private static final int THREAD_COUNT = 32;

    /*
     * 실제 성능 측정 횟수.
     */
    private static final int MEASURE_COUNT = 5;

    @Autowired
    private EventEntryService atomicEventEntryService;

    @Autowired
    private PessimisticEventEntryService
            pessimisticEventEntryService;

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
            "Atomic, Pessimistic, Optimistic 동시성 전략의 처리 시간을 비교한다"
    )
    void compareConcurrencyStrategies()
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

        /*
         * 각각의 전략을 동일한 조건으로
         * 5회씩 실행한다.
         */
        List<Long> atomicTimes =
                measureStrategy(
                        "Atomic Update",
                        atomicEventEntryService::enter
                );

        List<Long> pessimisticTimes =
                measureStrategy(
                        "Pessimistic Lock",
                        pessimisticEventEntryService::enter
                );

        List<Long> optimisticTimes =
                measureStrategy(
                        "Optimistic Lock",
                        optimisticEventEntryService::enter
                );

        /*
         * 최종 비교 결과 출력
         */
        printComparison(
                atomicTimes,
                pessimisticTimes,
                optimisticTimes
        );
    }

    private List<Long> measureStrategy(
            String strategyName,
            EntryExecutor entryExecutor
    ) throws Exception {

        List<Long> elapsedTimes =
                new ArrayList<>();

        System.out.println();
        System.out.println(
                "============================================"
        );

        System.out.println(
                strategyName
                        + " 성능 측정 시작"
        );

        System.out.println(
                "============================================"
        );

        for (
                int round = 1;
                round <= MEASURE_COUNT;
                round++
        ) {

            long elapsedMillis =
                    runOnce(
                            strategyName,
                            round,
                            entryExecutor
                    );

            elapsedTimes.add(
                    elapsedMillis
            );
        }

        printStrategySummary(
                strategyName,
                elapsedTimes
        );

        return elapsedTimes;
    }

    private long runOnce(
            String strategyName,
            int round,
            EntryExecutor entryExecutor
    ) throws Exception {

        /*
         * 각 측정마다 새로운 Event를 생성한다.
         *
         * 이전 테스트의 currentCount나 EventEntry가
         * 다음 측정에 영향을 주지 않도록 하기 위함이다.
         */
        Event event =
                eventRepository.save(
                        Event.create(
                                strategyName
                                        + " 성능 테스트 "
                                        + round,
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

        /*
         * 실제 Thread Pool 크기는 32개다.
         *
         * 32개의 worker가 준비되면
         * 동시에 출발시킨다.
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

        /*
         * 200개의 요청 등록
         */
        for (
                int i = 0;
                i < REQUEST_COUNT;
                i++
        ) {

            long userId =
                    i + 1L;

            executorService.submit(
                    () -> {

                        /*
                         * 처음 실행되는 32개의 worker가
                         * 준비되었음을 알린다.
                         *
                         * CountDownLatch는 0 아래로
                         * 내려가지 않기 때문에
                         * 이후 요청의 countDown은 문제없다.
                         */
                        readyLatch.countDown();

                        try {

                            startLatch.await();

                            entryExecutor.enter(
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

                            doneLatch.countDown();
                        }
                    }
            );
        }

        /*
         * 실제 worker 32개가 준비될 때까지 기다린다.
         */
        boolean ready =
                readyLatch.await(
                        5,
                        TimeUnit.SECONDS
                );

        assertThat(ready)
                .isTrue();

        /*
         * 모든 전략에서 정확히 같은 위치부터
         * 처리 시간을 측정한다.
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

        long elapsedMillis =
                TimeUnit.NANOSECONDS
                        .toMillis(
                                endTime - startTime
                        );

        /*
         * worker transaction에서 DB가 변경되었으므로
         * 최신 상태를 다시 조회하기 위해
         * Persistence Context를 초기화한다.
         */
        entityManager.clear();

        Event foundEvent =
                eventRepository
                        .findById(eventId)
                        .orElseThrow();

        long actualEntryCount =
                eventEntryRepository
                        .countByEventId(
                                eventId
                        );

        /*
         * 성능을 측정하는 테스트라고 해도
         * 정합성이 깨진 결과를 성능 데이터로
         * 사용해서는 안 된다.
         *
         * 따라서 매 회차마다 정합성도 검증한다.
         */
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
                foundEvent.getCurrentCount()
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
                foundEvent.getCurrentCount()
        )
                .isEqualTo(
                        actualEntryCount
                );

        System.out.printf(
                "%s - %d회: %d ms "
                        + "(성공=%d, 실패=%d)%n",
                strategyName,
                round,
                elapsedMillis,
                successCount.get(),
                failureCount.get()
        );

        return elapsedMillis;
    }

    private void printStrategySummary(
            String strategyName,
            List<Long> elapsedTimes
    ) {

        long min =
                elapsedTimes
                        .stream()
                        .mapToLong(
                                Long::longValue
                        )
                        .min()
                        .orElseThrow();

        long max =
                elapsedTimes
                        .stream()
                        .mapToLong(
                                Long::longValue
                        )
                        .max()
                        .orElseThrow();

        double average =
                elapsedTimes
                        .stream()
                        .mapToLong(
                                Long::longValue
                        )
                        .average()
                        .orElseThrow();

        System.out.println(
                "--------------------------------------------"
        );

        System.out.println(
                strategyName
                        + " 측정 결과"
        );

        System.out.println(
                "측정값 = "
                        + elapsedTimes
        );

        System.out.printf(
                "평균 = %.2f ms%n",
                average
        );

        System.out.println(
                "최소 = "
                        + min
                        + " ms"
        );

        System.out.println(
                "최대 = "
                        + max
                        + " ms"
        );

        System.out.println(
                "--------------------------------------------"
        );
    }

    private void printComparison(
            List<Long> atomicTimes,
            List<Long> pessimisticTimes,
            List<Long> optimisticTimes
    ) {

        PerformanceSummary atomic =
                PerformanceSummary.from(
                        atomicTimes
                );

        PerformanceSummary pessimistic =
                PerformanceSummary.from(
                        pessimisticTimes
                );

        PerformanceSummary optimistic =
                PerformanceSummary.from(
                        optimisticTimes
                );

        System.out.println();
        System.out.println(
                "=============================================================="
        );

        System.out.println(
                "동시성 전략 최종 성능 비교"
        );

        System.out.println(
                "조건: 요청 "
                        + REQUEST_COUNT
                        + " / 정원 "
                        + CAPACITY
                        + " / Thread "
                        + THREAD_COUNT
                        + " / "
                        + MEASURE_COUNT
                        + "회 측정"
        );

        System.out.println(
                "--------------------------------------------------------------"
        );

        System.out.printf(
                "%-20s %12s %12s %12s%n",
                "Strategy",
                "Average",
                "Min",
                "Max"
        );

        System.out.printf(
                "%-20s %10.2fms %10dms %10dms%n",
                "Atomic Update",
                atomic.average(),
                atomic.min(),
                atomic.max()
        );

        System.out.printf(
                "%-20s %10.2fms %10dms %10dms%n",
                "Pessimistic Lock",
                pessimistic.average(),
                pessimistic.min(),
                pessimistic.max()
        );

        System.out.printf(
                "%-20s %10.2fms %10dms %10dms%n",
                "Optimistic Lock",
                optimistic.average(),
                optimistic.min(),
                optimistic.max()
        );

        System.out.println(
                "=============================================================="
        );
    }

    /*
     * 세 Service의 enter 메서드를
     * 동일한 방식으로 호출하기 위한 함수형 인터페이스.
     */
    @FunctionalInterface
    private interface EntryExecutor {

        void enter(
                Long eventId,
                Long userId
        );
    }

    /*
     * 측정 결과를 평균 / 최소 / 최대 형태로
     * 관리하기 위한 간단한 record.
     */
    private record PerformanceSummary(
            double average,
            long min,
            long max
    ) {

        private static PerformanceSummary from(
                List<Long> elapsedTimes
        ) {

            double average =
                    elapsedTimes
                            .stream()
                            .mapToLong(
                                    Long::longValue
                            )
                            .average()
                            .orElseThrow();

            long min =
                    elapsedTimes
                            .stream()
                            .mapToLong(
                                    Long::longValue
                            )
                            .min()
                            .orElseThrow();

            long max =
                    elapsedTimes
                            .stream()
                            .mapToLong(
                                    Long::longValue
                            )
                            .max()
                            .orElseThrow();

            return new PerformanceSummary(
                    average,
                    min,
                    max
            );
        }
    }
}