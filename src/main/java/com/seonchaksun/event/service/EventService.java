package com.seonchaksun.event.service;

import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.dto.EventCreateRequest;
import com.seonchaksun.event.dto.EventResponse;
import com.seonchaksun.event.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        Event event = Event.create(
                request.name(),
                request.capacity(),
                request.openAt(),
                request.closeAt()
        );

        Event savedEvent = eventRepository.save(event);

        return EventResponse.from(savedEvent);
    }
}