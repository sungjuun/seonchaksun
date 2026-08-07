package com.seonchaksun.entry.controller;

import com.seonchaksun.entry.dto.EventEntryRequest;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.service.EventEntryService;
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

    private final EventEntryService eventEntryService;

    public EventEntryController(
            EventEntryService eventEntryService
    ) {
        this.eventEntryService =
                eventEntryService;
    }

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

        URI location = URI.create(
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