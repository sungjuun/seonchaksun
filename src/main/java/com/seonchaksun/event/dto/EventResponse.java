package com.seonchaksun.event.dto;

import com.seonchaksun.event.domain.Event;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        int capacity,
        int currentCount,
        LocalDateTime openAt,
        LocalDateTime closeAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getCapacity(),
                event.getCurrentCount(),
                event.getOpenAt(),
                event.getCloseAt()
        );
    }
}