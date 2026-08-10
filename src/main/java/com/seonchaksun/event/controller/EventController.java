package com.seonchaksun.event.controller;

import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.event.dto.EventCreateRequest;
import com.seonchaksun.event.dto.EventResponse;
import com.seonchaksun.event.dto.EventStatusResponse;
import com.seonchaksun.event.service.EventService;
import com.seonchaksun.event.service.EventStatusService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventStatusService eventStatusService;

    public EventController(
            EventService eventService,
            EventStatusService eventStatusService
    ) {
        this.eventService =
                eventService;

        this.eventStatusService =
                eventStatusService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody
            EventCreateRequest request
    ) {

        EventResponse response =
                eventService.createEvent(
                        request
                );

        URI location =
                URI.create(
                        "/api/events/"
                                + response.id()
                );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(
            @PathVariable Long eventId
    ) {

        EventResponse response =
                eventService.getEvent(
                        eventId
                );

        return ResponseEntity.ok(
                response
        );
    }

    /*
     * 전략별 신청 현황 조회
     *
     * 예:
     *
     * /api/events/15/status?strategy=atomic
     * /api/events/15/status?strategy=redis
     */
    @GetMapping("/{eventId}/status")
    public ResponseEntity<EventStatusResponse>
    getEventStatus(
            @PathVariable Long eventId,
            @RequestParam String strategy
    ) {

        EntryStrategy entryStrategy =
                EntryStrategy.from(
                        strategy
                );

        EventStatusResponse response =
                eventStatusService
                        .getStatus(
                                eventId,
                                entryStrategy
                        );

        return ResponseEntity.ok(
                response
        );
    }
}