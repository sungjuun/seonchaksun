package com.seonchaksun.event.dto;

public record EventStatusResponse(
        Long eventId,
        int capacity,
        int currentCount,
        int remainingCount,
        String countSource
) {
}