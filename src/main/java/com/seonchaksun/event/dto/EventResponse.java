package com.seonchaksun.event.dto;

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
                description = """
                        현재 신청 인원.

                        Atomic, Pessimistic, Optimistic 전략에서는
                        MySQL events.current_count 값입니다.

                        Redis 전략의 신청 인원은 별도의
                        status API에서 Redis Counter 기준으로 조회합니다.
                        """,
                example = "4"
        )
        int currentCount,

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
                event.getOpenAt(),
                event.getCloseAt()
        );
    }
}