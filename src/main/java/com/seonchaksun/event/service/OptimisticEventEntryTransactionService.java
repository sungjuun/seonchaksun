package com.seonchaksun.entry.service;

import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class OptimisticEventEntryTransactionService {

    private final EventRepository eventRepository;

    private final EventEntryRepository
            eventEntryRepository;

    private final Clock clock;

    public OptimisticEventEntryTransactionService(
            EventRepository eventRepository,
            EventEntryRepository eventEntryRepository,
            Clock clock
    ) {
        this.eventRepository =
                eventRepository;

        this.eventEntryRepository =
                eventEntryRepository;

        this.clock = clock;
    }

    /*
     * 한 번의 신청 시도 = 하나의 Transaction
     *
     * Optimistic Lock 충돌이 발생하면
     * 이 Transaction 전체가 rollback된다.
     */
    @Transactional
    public EventEntryResponse enterOnce(
            Long eventId,
            Long userId
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

        validateDuplicateEntry(
                eventId,
                userId
        );

        LocalDateTime now =
                LocalDateTime.now(clock);

        /*
         * 현재 Event 상태를 기준으로
         * 신청 기간 및 정원을 확인하고
         * currentCount를 증가시킨다.
         */
        event.enter(now);

        EventEntry entry =
                EventEntry.create(
                        event,
                        userId,
                        now
                );

        EventEntry savedEntry =
                eventEntryRepository
                        .save(entry);

        /*
         * Event는 영속 상태이므로
         * save(event)를 하지 않아도
         * Dirty Checking으로 UPDATE된다.
         *
         * @Version 때문에 Hibernate가
         *
         * WHERE id = ?
         *   AND version = ?
         *
         * 조건을 함께 사용한다.
         */
        return EventEntryResponse.from(
                savedEntry
        );
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
}