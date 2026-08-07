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
public class EventEntryService {

    private final EventRepository eventRepository;
    private final EventEntryRepository eventEntryRepository;
    private final Clock clock;

    public EventEntryService(
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
        validateDuplicateEntry(
                eventId,
                userId
        );

        LocalDateTime now =
                LocalDateTime.now(clock);

        int updatedCount =
                eventRepository.incrementCurrentCount(
                        eventId,
                        now
                );

        if (updatedCount == 0) {
            throwEntryFailure(
                    eventId,
                    now
            );
        }

        Event event =
                eventRepository.getReferenceById(
                        eventId
                );

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

    private void throwEntryFailure(
            Long eventId,
            LocalDateTime now
    ) {
        Event event = eventRepository
                .findById(eventId)
                .orElseThrow(
                        () -> new EventNotFoundException(
                                eventId
                        )
                );

        /*
         * Atomic Update가 0건이었다면
         * 이벤트 없음 / 신청 기간 아님 / 정원 마감
         * 중 하나다.
         *
         * 이벤트 자체의 기존 도메인 규칙을 재사용해서
         * 구체적인 EventException을 발생시킨다.
         */
        event.enter(now);

        throw new IllegalStateException(
                "이벤트 신청 실패 원인을 확인할 수 없습니다."
        );
    }
}