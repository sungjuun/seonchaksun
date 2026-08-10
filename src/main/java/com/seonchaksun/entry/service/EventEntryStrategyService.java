package com.seonchaksun.entry.service;

import com.seonchaksun.entry.dto.EventEntryResponse;
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

    public EventEntryStrategyService(
            EventEntryService atomicEventEntryService,
            PessimisticEventEntryService pessimisticEventEntryService,
            OptimisticEventEntryService optimisticEventEntryService,
            RedisEventEntryService redisEventEntryService
    ) {

        this.atomicEventEntryService =
                atomicEventEntryService;

        this.pessimisticEventEntryService =
                pessimisticEventEntryService;

        this.optimisticEventEntryService =
                optimisticEventEntryService;

        this.redisEventEntryService =
                redisEventEntryService;
    }

    public EventEntryResponse enter(
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