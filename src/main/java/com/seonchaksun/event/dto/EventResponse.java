package com.seonchaksun.event.dto;

import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.event.domain.Event;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
        description = "이벤트 기본 정보 응답"
)
public record EventResponse(

        @Schema(
                description = "이벤트 ID",
                example = "15"
        )
        Long id,

        @Schema(
                description = "이벤트 이름",
                example = "한정판 키보드 사전예약"
        )
        String name,

        @Schema(
                description = "이벤트 최대 정원",
                example = "100"
        )
        int capacity,

        @Schema(
                description = "MySQL events.current_count 값",
                example = "4"
        )
        int currentCount,

        @Schema(
                description = "이 이벤트에 고정된 동시성 처리 전략",
                example = "ATOMIC"
        )
        EntryStrategy strategy,

        @Schema(
                description = "신청 시작 시간",
                example = "2026-08-11T14:00:00"
        )
        LocalDateTime openAt,

        @Schema(
                description = "신청 종료 시간",
                example = "2026-08-11T18:00:00"
        )
        LocalDateTime closeAt

) {

    public static EventResponse from(
            Event event
    ) {

        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getCapacity(),
                event.getCurrentCount(),
                event.getStrategy(),
                event.getOpenAt(),
                event.getCloseAt()
        );
    }

    /*
     * 기존 테스트 코드 호환용 생성자.
     */
    public EventResponse(
            Long id,
            String name,
            int capacity,
            int currentCount,
            LocalDateTime openAt,
            LocalDateTime closeAt
    ) {
        this(
                id,
                name,
                capacity,
                currentCount,
                EntryStrategy.ATOMIC,
                openAt,
                closeAt
        );
    }
}
