package com.seonchaksun.entry.redis;

import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventException;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.repository.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RedisEventEntryService {

    private final EventRepository eventRepository;
    private final EventEntryRepository eventEntryRepository;
    private final RedisCapacityService redisCapacityService;
    private final Clock clock;

    public RedisEventEntryService(
            EventRepository eventRepository,
            EventEntryRepository eventEntryRepository,
            RedisCapacityService redisCapacityService,
            Clock clock
    ) {
        this.eventRepository =
                eventRepository;

        this.eventEntryRepository =
                eventEntryRepository;

        this.redisCapacityService =
                redisCapacityService;

        this.clock =
                clock;
    }

    @Transactional
    public EventEntryResponse enter(
            Long eventId,
            Long userId
    ) {

        /*
         * 1. 이벤트 존재 여부 확인
         */
        Event event =
                eventRepository
                        .findById(eventId)
                        .orElseThrow(
                                () ->
                                        new EventNotFoundException(
                                                eventId
                                        )
                        );

        /*
         * 2. 일반적인 중복 신청을 빠르게 차단
         *
         * 동시 요청에 대한 최종 방어는
         * DB UNIQUE Constraint가 담당한다.
         */
        validateDuplicateEntry(
                eventId,
                userId
        );

        LocalDateTime now =
                LocalDateTime.now(clock);

        /*
         * 3. 신청 기간 검증
         *
         * Redis 전략에서는 Event.enter()를 호출하지 않는다.
         *
         * Event.enter()는 DB currentCount까지 증가시키기 때문이다.
         */
        event.validateEntryPeriod(
                now
        );

        /*
         * 4. Redis에서 자리 예약
         *
         * Lua Script가
         *
         * GET
         * 정원 비교
         * INCR
         *
         * 를 원자적으로 수행한다.
         */
        boolean reserved =
                redisCapacityService.reserve(
                        eventId,
                        event.getCapacity()
                );

        if (!reserved) {
            throw new EventException(
                    "이벤트 신청이 마감되었습니다."
            );
        }

        /*
         * 여기서부터 MySQL 저장이 실패하면
         * Redis 예약을 반드시 반환해야 한다.
         */
        try {

            EventEntry entry =
                    EventEntry.create(
                            event,
                            userId,
                            now
                    );

            /*
             * UNIQUE Constraint 오류를
             * 이 try-catch 안에서 감지하기 위해
             * saveAndFlush() 사용.
             */
            EventEntry savedEntry =
                    eventEntryRepository
                            .saveAndFlush(
                                    entry
                            );

            return EventEntryResponse.from(
                    savedEntry
            );

        } catch (
                DataIntegrityViolationException e
        ) {

            /*
             * Redis에서는 이미 자리를 하나 차지했으므로
             * DB INSERT 실패 시 보상한다.
             */
            compensateReservation(
                    eventId
            );

            throw new DuplicateEntryException(
                    eventId,
                    userId
            );

        } catch (
                RuntimeException e
        ) {

            /*
             * 중복 이외의 DB/application 오류에서도
             * Redis 예약이 남지 않도록 보상한다.
             */
            compensateReservation(
                    eventId
            );

            throw e;
        }
    }

    private void validateDuplicateEntry(
            Long eventId,
            Long userId
    ) {

        boolean exists =
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                eventId,
                                userId
                        );

        if (exists) {
            throw new DuplicateEntryException(
                    eventId,
                    userId
            );
        }
    }

    private void compensateReservation(
            Long eventId
    ) {

        boolean released =
                redisCapacityService.release(
                        eventId
                );

        if (!released) {
            throw new IllegalStateException(
                    "Redis 예약 보상에 실패했습니다. eventId="
                            + eventId
            );
        }
    }
}