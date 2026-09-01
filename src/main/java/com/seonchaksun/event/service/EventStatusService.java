package com.seonchaksun.event.service;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;
import com.seonchaksun.entry.redis.RedisCapacityService;
import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.dto.EventStatusResponse;
import com.seonchaksun.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventStatusService {

    private final EventRepository eventRepository;
    private final RedisCapacityService redisCapacityService;

    public EventStatusService(
            EventRepository eventRepository,
            RedisCapacityService redisCapacityService
    ) {
        this.eventRepository = eventRepository;
        this.redisCapacityService = redisCapacityService;
    }

    @Transactional(readOnly = true)
    public EventStatusResponse getStatus(
            Long eventId,
            EntryStrategy requestedStrategy
    ) {

        Event event =
                eventRepository
                        .findById(eventId)
                        .orElseThrow(
                                () ->
                                        new EventNotFoundException(
                                                eventId
                                        )
                        );

        validateStrategy(
                event,
                requestedStrategy
        );

        int currentCount;
        String countSource;

        if (event.getStrategy() == EntryStrategy.REDIS) {

            long redisCount =
                    redisCapacityService
                            .getCurrentCount(
                                    eventId
                            );

            currentCount =
                    Math.toIntExact(
                            redisCount
                    );

            countSource =
                    "REDIS";

        } else {

            currentCount =
                    event.getCurrentCount();

            countSource =
                    "MYSQL";
        }

        int remainingCount =
                Math.max(
                        event.getCapacity()
                                - currentCount,
                        0
                );

        return new EventStatusResponse(
                event.getId(),
                event.getCapacity(),
                currentCount,
                remainingCount,
                countSource
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
}
