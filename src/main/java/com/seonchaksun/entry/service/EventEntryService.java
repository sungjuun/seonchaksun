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
        Event event = eventRepository
                .findById(eventId)
                .orElseThrow(
                        () -> new EventNotFoundException(
                                eventId
                        )
                );

        validateDuplicateEntry(
                eventId,
                userId
        );

        LocalDateTime now =
                LocalDateTime.now(clock);

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