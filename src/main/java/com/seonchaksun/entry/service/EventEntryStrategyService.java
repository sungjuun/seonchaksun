package com.seonchaksun.entry.service;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.metric.EntryMetrics;
import com.seonchaksun.entry.redis.RedisEventEntryService;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.repository.EventRepository;
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

    private final EventRepository
            eventRepository;

    private final EntryMetrics
            entryMetrics;

    public EventEntryStrategyService(
            EventEntryService atomicEventEntryService,
            PessimisticEventEntryService pessimisticEventEntryService,
            OptimisticEventEntryService optimisticEventEntryService,
            RedisEventEntryService redisEventEntryService,
            EventRepository eventRepository,
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

        this.eventRepository =
                eventRepository;

        this.entryMetrics =
                entryMetrics;
    }

    /*
     * 이벤트에 저장된 전략을 자동으로 사용한다.
     *
     * /api/events/{eventId}/entries 기본 신청 API가
     * Redis 이벤트를 Atomic 방식으로 처리하는 식의 혼용을 막는다.
     */
    public EventEntryResponse enterForEvent(
            Long eventId,
            Long userId
    ) {
        Event event = findEvent(eventId);

        return executeWithMetrics(
                event.getStrategy(),
                eventId,
                userId
        );
    }

    public EventEntryResponse enter(
            EntryStrategy strategy,
            Long eventId,
            Long userId
    ) {

        Event event = findEvent(eventId);

        validateStrategy(
                event,
                strategy
        );

        return executeWithMetrics(
                strategy,
                eventId,
                userId
        );
    }

    private EventEntryResponse executeWithMetrics(
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

    private Event findEvent(
            Long eventId
    ) {
        return eventRepository
                .findById(eventId)
                .orElseThrow(
                        () ->
                                new EventNotFoundException(
                                        eventId
                                )
                );
    }

    private void validateStrategy(
            Event event,
            EntryStrategy requestedStrategy
    ) {
        if (event.getStrategy() == requestedStrategy) {
            return;
        }

        throw new BusinessException(
                ErrorCode.STRATEGY_MISMATCH,
                "이 이벤트는 "
                        + event.getStrategy()
                        + " 전략으로 생성되었습니다. 요청 전략="
                        + requestedStrategy
        );
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
