package com.seonchaksun.entry.dto;

import com.seonchaksun.entry.domain.EventEntry;

import java.time.LocalDateTime;

public record EventEntryResponse(
        Long id,
        Long eventId,
        Long userId,
        LocalDateTime createdAt
) {

    public static EventEntryResponse from(
            EventEntry entry
    ) {
        return new EventEntryResponse(
                entry.getId(),
                entry.getEvent().getId(),
                entry.getUserId(),
                entry.getCreatedAt()
        );
    }
}