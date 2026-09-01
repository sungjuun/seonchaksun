package com.seonchaksun.event.controller;

import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.event.dto.EventCreateRequest;
import com.seonchaksun.event.dto.EventResponse;
import com.seonchaksun.event.dto.EventStatusResponse;
import com.seonchaksun.event.service.EventService;
import com.seonchaksun.event.service.EventStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(
        name = "Event",
        description = "선착순 이벤트 생성 및 조회 API"
)
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventStatusService eventStatusService;

    public EventController(
            EventService eventService,
            EventStatusService eventStatusService
    ) {
        this.eventService = eventService;
        this.eventStatusService = eventStatusService;
    }

    @Operation(
            summary = "이벤트 생성",
            description = """
                    새로운 선착순 이벤트를 생성합니다.

                    이벤트 이름, 정원, 동시성 처리 전략, 신청 시작 시간,
                    신청 마감 시간을 입력합니다.

                    하나의 이벤트에는 하나의 처리 전략이 고정됩니다.
                    시작 시간은 종료 시간보다 이전이어야 합니다.
                    """
    )
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid
            @RequestBody
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

    @Operation(
            summary = "이벤트 조회",
            description = """
                    Event ID를 이용해 이벤트의 기본 정보를 조회합니다.

                    이벤트 이름, 정원, 현재 신청 인원,
                    신청 시작 및 종료 시간을 확인할 수 있습니다.
                    """
    )
    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(

            @Parameter(
                    description = "조회할 이벤트 ID",
                    example = "15"
            )
            @PathVariable
            Long eventId

    ) {

        EventResponse response =
                eventService.getEvent(
                        eventId
                );

        return ResponseEntity.ok(
                response
        );
    }

    @Operation(
            summary = "전략별 신청 현황 조회",
            description = """
                    이벤트에 고정된 동시성 제어 전략을 기준으로
                    현재 신청 인원과 남은 정원을 조회합니다.

                    요청한 strategy가 이벤트에 저장된 strategy와 다르면
                    400 STRATEGY_MISMATCH를 반환합니다.

                    Atomic, Pessimistic, Optimistic 전략은
                    MySQL의 events.current_count를 기준으로 합니다.

                    Redis 전략은 Redis Counter를
                    신청 인원의 기준으로 사용합니다.

                    지원 전략:
                    - atomic
                    - pessimistic
                    - optimistic
                    - redis
                    """
    )
    @GetMapping("/{eventId}/status")
    public ResponseEntity<EventStatusResponse> getEventStatus(

            @Parameter(
                    description = "조회할 이벤트 ID",
                    example = "15"
            )
            @PathVariable
            Long eventId,

            @Parameter(
                    description = """
                            신청 현황을 조회할 동시성 전략

                            사용 가능 값:
                            atomic, pessimistic, optimistic, redis
                            """,
                    example = "atomic"
            )
            @RequestParam
            String strategy

    ) {

        EntryStrategy entryStrategy =
                EntryStrategy.from(
                        strategy
                );

        EventStatusResponse response =
                eventStatusService.getStatus(
                        eventId,
                        entryStrategy
                );

        return ResponseEntity.ok(
                response
        );
    }
}