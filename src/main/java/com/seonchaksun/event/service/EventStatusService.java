package com.seonchaksun.event.service;

import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.dto.EventStatusResponse;
import com.seonchaksun.event.repository.EventRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventStatusService {

    private static final String REDIS_CAPACITY_KEY_PREFIX =
            "event:capacity:";

    private final EventRepository eventRepository;
    private final StringRedisTemplate redisTemplate;

    public EventStatusService(
            EventRepository eventRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.eventRepository = eventRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public EventStatusResponse getStatus(
            Long eventId,
            EntryStrategy strategy
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

        int currentCount;
        String countSource;

        /*
         * Redis 전략에서는 events.current_count를
         * 사용하지 않는다.
         *
         * Redis의 event:capacity:{eventId}
         * Counter가 현재 신청 인원의 기준이다.
         */
        if (strategy == EntryStrategy.REDIS) {

            currentCount =
                    getRedisCurrentCount(
                            eventId
                    );

            countSource =
                    "REDIS";

        } else {

            /*
             * Atomic / Pessimistic / Optimistic은
             * MySQL events.current_count를 사용한다.
             */
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

    /*
     * Redis 정원 Counter 조회
     *
     * Key:
     * event:capacity:{eventId}
     *
     * 아직 Redis 전략으로 신청한 사용자가 없으면
     * Key 자체가 없을 수 있기 때문에
     * null인 경우 0으로 처리한다.
     */
    private int getRedisCurrentCount(
            Long eventId
    ) {

        String key =
                REDIS_CAPACITY_KEY_PREFIX
                        + eventId;

        String value =
                redisTemplate
                        .opsForValue()
                        .get(key);

        if (value == null) {
            return 0;
        }

        return Integer.parseInt(
                value
        );
    }
}