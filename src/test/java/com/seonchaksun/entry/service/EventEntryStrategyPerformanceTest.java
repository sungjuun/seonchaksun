package com.seonchaksun.entry.service;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.redis.RedisCapacityService;
import com.seonchaksun.entry.redis.RedisEventEntryService;
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
     * 각 전략의 실제 성능 측정 횟수.
     */
    private static final int MEASURE_COUNT = 5;

    @Autowired
    private EventEntryService
            atomicEventEntryService;

    @Autowired
    private PessimisticEventEntryService
            pessimisticEventEntryService;

    @Autowired
    private OptimisticEventEntryService
            optimisticEventEntryService;

    /*
     * Redis + MySQL 전략.
     */
    @Autowired
    private RedisEventEntryService
            redisEventEntryService;

    /*
     * Redis 전략의 정합성 검증 및
     * 테스트 데이터 초기화에 사용한다.
     */
    @Autowired
    private RedisCapacityService
            redisCapacityService;

    @Autowired
    private EventRepository
            eventRepository;

    @Autowired
    private EventEntryRepository
            eventEntryRepository;

    @Autowired
    private EntityManager
            entityManager;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "Atomic, Pessimistic, Optimistic, Redis 동시성 전략의 처리 시간을 비교한다"
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
         * 모든 전략을 동일한 조건으로
         * 각각 5회씩 실행한다.
         */
        List<Long> atomicTimes =
                measureStrategy(
                        "Atomic Update",
                        StrategyType.DATABASE,
                        atomicEventEntryService::enter
                );

        List<Long> pessimisticTimes =
                measureStrategy(
                        "Pessimistic Lock",
                        StrategyType.DATABASE,
                        pessimisticEventEntryService::enter
                );

        List<Long> optimisticTimes =
                measureStrategy(
                        "Optimistic Lock",
                        StrategyType.DATABASE,
                        optimisticEventEntryService::enter
                );

        List<Long> redisTimes =
                measureStrategy(
                        "Redis + MySQL",
                        StrategyType.REDIS,
                        redisEventEntryService::enter
                );

        /*
         * 최종 비교 결과 출력
         */
        printComparison(
                atomicTimes,
                pessimisticTimes,
                optimisticTimes,
                redisTimes
        );
    }

    private List<Long> measureStrategy(
            String strategyName,
            StrategyType strategyType,
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
                            strategyType,
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
            StrategyType strategyType,
            int round,
            EntryExecutor entryExecutor
    ) throws Exception {

        /*
         * 각 측정마다 새로운 Event를 생성한다.
         *
         * 이전 측정의 currentCount나 EventEntry가
         * 다음 측정에 영향을 주지 않도록 한다.
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

        /*
         * Redis 전략의 경우
         * 혹시 동일 eventId에 테스트 값이 남아 있다면
         * 측정 전에 제거한다.
         */
        if (strategyType == StrategyType.REDIS) {

            redisCapacityService.clear(
                    eventId
            );
        }

        try {

            ExecutorService executorService =
                    Executors.newFixedThreadPool(
                            THREAD_COUNT
                    );

            /*
             * 실제 Thread Pool 크기는 32개다.
             *
             * 32개의 Worker가 준비되면
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
             * 200개의 서로 다른 사용자 요청을 등록한다.
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
                             * 처음 실행되는 32개의 Worker가
                             * 준비되었음을 알린다.
                             *
                             * CountDownLatch는 0 아래로
                             * 내려가지 않기 때문에
                             * 이후 요청의 countDown()은
                             * 문제가 없다.
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
             * 실제 Worker 32개가 준비될 때까지 기다린다.
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
             * Worker Transaction에서 DB 상태가
             * 변경되었기 때문에 최신 데이터를
             * 다시 읽기 위해 Persistence Context를
             * 초기화한다.
             */
            entityManager.clear();

            Event foundEvent =
                    eventRepository
                            .findById(
                                    eventId
                            )
                            .orElseThrow();

            long actualEntryCount =
                    eventEntryRepository
                            .countByEventId(
                                    eventId
                            );

            /*
             * 공통 정합성 검증.
             *
             * 어떤 전략을 사용하더라도
             *
             * 성공 100
             * 실패 100
             * EventEntry 100
             *
             * 이어야 한다.
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
                    actualEntryCount
            )
                    .isEqualTo(
                            CAPACITY
                    );

            /*
             * 전략별 정합성 검증.
             */
            if (
                    strategyType
                            == StrategyType.REDIS
            ) {

                verifyRedisConsistency(
                        eventId,
                        foundEvent,
                        actualEntryCount
                );

            } else {

                verifyDatabaseConsistency(
                        foundEvent,
                        actualEntryCount
                );
            }

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

        } finally {

            /*
             * Redis 전략은 MySQL Transaction과 별도로
             * Redis 데이터가 존재하므로
             * 매 측정 후 반드시 제거한다.
             */
            if (
                    strategyType
                            == StrategyType.REDIS
            ) {

                redisCapacityService.clear(
                        eventId
                );
            }
        }
    }

    /*
     * Atomic / Pessimistic / Optimistic 전략의
     * 정합성 검증.
     *
     * 이 세 전략에서는 MySQL events.current_count가
     * 정원 관리의 기준이다.
     */
    private void verifyDatabaseConsistency(
            Event event,
            long actualEntryCount
    ) {

        assertThat(
                event.getCurrentCount()
        )
                .isEqualTo(
                        CAPACITY
                );

        assertThat(
                event.getCurrentCount()
        )
                .isEqualTo(
                        actualEntryCount
                );
    }

    /*
     * Redis 전략의 정합성 검증.
     *
     * Redis 전략에서는
     * events.current_count가 아니라
     * Redis Counter가 정원 제어의 기준이다.
     */
    private void verifyRedisConsistency(
            Long eventId,
            Event event,
            long actualEntryCount
    ) {

        long redisCount =
                redisCapacityService
                        .getCurrentCount(
                                eventId
                        );

        /*
         * Redis에서 정확히 정원까지만
         * 예약되었는지 확인한다.
         */
        assertThat(
                redisCount
        )
                .isEqualTo(
                        CAPACITY
                );

        /*
         * Redis가 허용한 신청 수와
         * MySQL에 실제 저장된 신청 수가
         * 일치해야 한다.
         */
        assertThat(
                redisCount
        )
                .isEqualTo(
                        actualEntryCount
                );

        /*
         * 현재 Redis 전략에서는
         * DB events.current_count를 사용하지 않는다.
         *
         * 따라서 이 값은 생성 당시 값인
         * 0을 유지하는 것이 현재 설계상 정상이다.
         */
        assertThat(
                event.getCurrentCount()
        )
                .isZero();
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
            List<Long> optimisticTimes,
            List<Long> redisTimes
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

        PerformanceSummary redis =
                PerformanceSummary.from(
                        redisTimes
                );

        System.out.println();

        System.out.println(
                "======================================================================"
        );

        System.out.println(
                "동시성 전략 최종 성능 비교"
        );

        System.out.println(
                "조건: 요청 "
                        + REQUEST_COUNT
                        + " / 정원 "
                        + CAPACITY
                        + " / 최대 동시 Thread "
                        + THREAD_COUNT
                        + " / "
                        + MEASURE_COUNT
                        + "회 측정"
        );

        System.out.println(
                "----------------------------------------------------------------------"
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

        System.out.printf(
                "%-20s %10.2fms %10dms %10dms%n",
                "Redis + MySQL",
                redis.average(),
                redis.min(),
                redis.max()
        );

        System.out.println(
                "======================================================================"
        );
    }

    /*
     * 네 Service의 enter() 메서드를
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
     * DB 기반 전략과 Redis 전략은
     * 정합성 검증 기준이 다르기 때문에
     * 이를 명시적으로 구분한다.
     */
    private enum StrategyType {

        DATABASE,

        REDIS
    }

    /*
     * 측정 결과를 평균 / 최소 / 최대 형태로
     * 관리하기 위한 record.
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