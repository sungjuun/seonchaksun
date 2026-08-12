package com.seonchaksun.entry.controller;

import com.seonchaksun.entry.dto.EventEntryRequest;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.entry.service.EventEntryService;
import com.seonchaksun.entry.service.EventEntryStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(
        name = "Event Entry",
        description = "선착순 이벤트 신청 및 동시성 전략 테스트 API"
)
@RestController
@RequestMapping("/api/events/{eventId}/entries")
public class EventEntryController {

    private final EventEntryService eventEntryService;

    private final EventEntryStrategyService eventEntryStrategyService;

    public EventEntryController(
            EventEntryService eventEntryService,
            EventEntryStrategyService eventEntryStrategyService
    ) {
        this.eventEntryService = eventEntryService;
        this.eventEntryStrategyService = eventEntryStrategyService;
    }

    @Operation(
            summary = "이벤트 신청",
            description = """
                    기본 동시성 처리 전략을 사용해
                    선착순 이벤트에 신청합니다.

                    현재 기본 전략은 Atomic Update입니다.

                    신청 성공 시 HTTP 201 Created를 반환합니다.

                    동일 사용자가 이미 신청한 경우에는
                    중복 신청으로 처리됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "신청 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = EventEntryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "정원 초과 또는 신청 기간이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "정원 초과",
                                            value = """
                                                    {
                                                      "message": "이벤트 정원이 모두 찼습니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "신청 기간 아님",
                                            value = """
                                                    {
                                                      "message": "이벤트 신청 기간이 아닙니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 이벤트",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이벤트를 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "동일 사용자의 중복 신청",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 신청한 사용자입니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping
    public ResponseEntity<EventEntryResponse> enter(

            @Parameter(
                    description = "신청할 이벤트 ID",
                    example = "15"
            )
            @PathVariable
            Long eventId,

            @Valid
            @RequestBody
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

    @Operation(
            summary = "동시성 전략을 선택하여 이벤트 신청",
            description = """
                    지정한 동시성 제어 전략을 이용해
                    동일한 선착순 이벤트에 신청합니다.

                    이 API는 각 동시성 전략의 정합성과
                    처리 성능을 비교하기 위해 사용합니다.

                    지원 전략:

                    - atomic
                    - pessimistic
                    - optimistic
                    - redis
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "신청 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = EventEntryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 전략, 정원 초과 또는 신청 기간이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "정원 초과",
                                            value = """
                                                    {
                                                      "message": "이벤트 정원이 모두 찼습니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "신청 기간 아님",
                                            value = """
                                                    {
                                                      "message": "이벤트 신청 기간이 아닙니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지원하지 않는 전략",
                                            value = """
                                                    {
                                                      "message": "지원하지 않는 동시성 전략입니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 이벤트",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이벤트를 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "동일 사용자의 중복 신청",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 신청한 사용자입니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/strategies/{strategy}")
    public ResponseEntity<EventEntryResponse> enterWithStrategy(

            @Parameter(
                    description = "신청할 이벤트 ID",
                    example = "15"
            )
            @PathVariable
            Long eventId,

            @Parameter(
                    description = """
                            적용할 동시성 전략

                            사용 가능 값:
                            atomic, pessimistic, optimistic, redis
                            """,
                    example = "atomic"
            )
            @PathVariable
            String strategy,

            @Valid
            @RequestBody
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

    private ResponseEntity<EventEntryResponse> createResponse(
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