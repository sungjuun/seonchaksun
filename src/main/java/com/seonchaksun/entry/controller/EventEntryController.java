package com.seonchaksun.entry.controller;

import com.seonchaksun.entry.dto.EventEntryRequest;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.entry.service.EventEntryService;
import com.seonchaksun.entry.service.EventEntryStrategyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/events/{eventId}/entries")
public class EventEntryController {

    private final EventEntryService
            eventEntryService;

    private final EventEntryStrategyService
            eventEntryStrategyService;

    public EventEntryController(
            EventEntryService eventEntryService,
            EventEntryStrategyService eventEntryStrategyService
    ) {

        this.eventEntryService =
                eventEntryService;

        this.eventEntryStrategyService =
                eventEntryStrategyService;
    }

    /*
     * 기존 신청 API.
     *
     * 현재 기본 전략은 Atomic Update다.
     */
    @PostMapping
    public ResponseEntity<EventEntryResponse> enter(
            @PathVariable Long eventId,
            @Valid @RequestBody
            EventEntryRequest request
    ) {

        EventEntryResponse response =
                eventEntryService.enter(
                        eventId,
                        request.userId()
                );

        return createResponse(
                eventId,
                response
        );
    }

    /*
     * 동시성 전략 비교 / 부하 테스트용 API.
     *
     * 예:
     *
     * /strategies/atomic
     * /strategies/pessimistic
     * /strategies/optimistic
     * /strategies/redis
     */
    @PostMapping(
            "/strategies/{strategy}"
    )
    public ResponseEntity<EventEntryResponse>
    enterWithStrategy(
            @PathVariable Long eventId,
            @PathVariable String strategy,
            @Valid @RequestBody
            EventEntryRequest request
    ) {

        EntryStrategy entryStrategy =
                EntryStrategy.from(
                        strategy
                );

        EventEntryResponse response =
                eventEntryStrategyService.enter(
                        entryStrategy,
                        eventId,
                        request.userId()
                );

        return createResponse(
                eventId,
                response
        );
    }

    private ResponseEntity<EventEntryResponse>
    createResponse(
            Long eventId,
            EventEntryResponse response
    ) {

        URI location =
                URI.create(
                        "/api/events/"
                                + eventId
                                + "/entries/"
                                + response.id()
                );

        return ResponseEntity
                .created(location)
                .body(response);
    }
}