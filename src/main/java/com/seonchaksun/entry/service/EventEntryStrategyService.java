package com.seonchaksun.entry.service;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.metric.EntryMetrics;
import com.seonchaksun.entry.redis.RedisEventEntryService;
import org.springframework.stereotype.Service;

@Service
public class EventEntryStrategyService {

    private final EventEntryService
            atomicEventEntryService;

    private final PessimisticEventEntryService
            pessimisticEventEntryService;

    private final OptimisticEventEntryService
            optimisticEventEntryService;

    private final RedisEventEntryService
            redisEventEntryService;

    private final EntryMetrics
            entryMetrics;

    public EventEntryStrategyService(
            EventEntryService atomicEventEntryService,
            PessimisticEventEntryService pessimisticEventEntryService,
            OptimisticEventEntryService optimisticEventEntryService,
            RedisEventEntryService redisEventEntryService,
            EntryMetrics entryMetrics
    ) {

        this.atomicEventEntryService =
                atomicEventEntryService;

        this.pessimisticEventEntryService =
                pessimisticEventEntryService;

        this.optimisticEventEntryService =
                optimisticEventEntryService;

        this.redisEventEntryService =
                redisEventEntryService;

        this.entryMetrics =
                entryMetrics;
    }

    public EventEntryResponse enter(
            EntryStrategy strategy,
            Long eventId,
            Long userId
    ) {

        long startTime =
                System.nanoTime();

        try {

            EventEntryResponse response =
                    executeStrategy(
                            strategy,
                            eventId,
                            userId
                    );

            long duration =
                    System.nanoTime()
                            - startTime;

            entryMetrics.recordSuccess(
                    strategy,
                    duration
            );

            return response;

        } catch (BusinessException e) {

            long duration =
                    System.nanoTime()
                            - startTime;

            entryMetrics.recordBusinessFailure(
                    strategy,
                    duration
            );

            throw e;

        } catch (RuntimeException e) {

            long duration =
                    System.nanoTime()
                            - startTime;

            entryMetrics.recordUnexpectedFailure(
                    strategy,
                    duration
            );

            throw e;
        }
    }

    private EventEntryResponse executeStrategy(
            EntryStrategy strategy,
            Long eventId,
            Long userId
    ) {

        return switch (strategy) {

            case ATOMIC ->
                    atomicEventEntryService.enter(
                            eventId,
                            userId
                    );

            case PESSIMISTIC ->
                    pessimisticEventEntryService.enter(
                            eventId,
                            userId
                    );

            case OPTIMISTIC ->
                    optimisticEventEntryService.enter(
                            eventId,
                            userId
                    );

            case REDIS ->
                    redisEventEntryService.enter(
                            eventId,
                            userId
                    );
        };
    }
}