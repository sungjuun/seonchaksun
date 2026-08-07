package com.seonchaksun.entry.service;

import com.seonchaksun.entry.dto.EventEntryResponse;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class OptimisticEventEntryService {

    /*
     * 같은 Event 하나에 200개 요청을 집중시키는
     * 극단적인 테스트이므로 재시도 횟수를 넉넉하게 둔다.
     *
     * 나중에 성능 비교 후 이 값도 분석 대상이 된다.
     */
    private static final int MAX_RETRY = 100;

    private final OptimisticEventEntryTransactionService
            transactionService;

    public OptimisticEventEntryService(
            OptimisticEventEntryTransactionService
                    transactionService
    ) {
        this.transactionService =
                transactionService;
    }

    public EventEntryResponse enter(
            Long eventId,
            Long userId
    ) {

        int retryCount = 0;

        while (true) {

            try {

                return transactionService
                        .enterOnce(
                                eventId,
                                userId
                        );

            } catch (
                    ObjectOptimisticLockingFailureException
                    | CannotAcquireLockException e
            ) {

                retryCount++;

                if (retryCount >= MAX_RETRY) {
                    throw e;
                }

                /*
                 * 모든 충돌 요청이 즉시 같은 순간에
                 * 다시 DB를 공격하면 또 충돌할 가능성이 높다.
                 *
                 * 1~5ms 사이의 짧은 랜덤 Backoff를 둔다.
                 */
                sleepRandomBackoff();
            }
        }
    }

    private void sleepRandomBackoff() {

        long backoffMillis =
                ThreadLocalRandom
                        .current()
                        .nextLong(
                                1,
                                6
                        );

        try {

            Thread.sleep(
                    backoffMillis
            );

        } catch (
                InterruptedException e
        ) {

            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "낙관적 락 재시도 대기 중 인터럽트가 발생했습니다.",
                    e
            );
        }
    }
}