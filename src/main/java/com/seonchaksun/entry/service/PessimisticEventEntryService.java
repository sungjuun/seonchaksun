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
public class PessimisticEventEntryService {

    private final EventRepository eventRepository;
    private final EventEntryRepository eventEntryRepository;
    private final Clock clock;

    public PessimisticEventEntryService(
            EventRepository eventRepository,
            EventEntryRepository eventEntryRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.eventEntryRepository =
                eventEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public EventEntryResponse enter(
            Long eventId,
            Long userId
    ) {
        /*
         * 여기서 Event row에 PESSIMISTIC_WRITE 락 획득.
         *
         * 동일 Event에 대한 다른 신청 트랜잭션들은
         * 이 트랜잭션이 끝날 때까지 기다린다.
         */
        Event event =
                eventRepository
                        .findByIdWithPessimisticLock(
                                eventId
                        )
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
         * 락을 가진 상태이므로
         * currentCount를 읽고 검사하고 증가하는 동안
         * 다른 트랜잭션이 끼어들 수 없다.
         */
        event.enter(now);

        EventEntry entry =
                EventEntry.create(
                        event,
                        userId,
                        now
                );

        EventEntry savedEntry =
                eventEntryRepository.save(entry);

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