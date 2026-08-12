package com.seonchaksun.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "선택한 동시성 전략 기준 이벤트 신청 현황"
)
public record EventStatusResponse(

        @Schema(
                description = "이벤트 ID",
                example = "15"
        )
        Long eventId,

        @Schema(
                description = "이벤트 최대 정원",
                example = "100"
        )
        int capacity,

        @Schema(
                description = """
                        선택한 전략 기준 현재 신청 인원.

                        DB 기반 전략은 MySQL current_count,
                        Redis 전략은 Redis Counter를 사용합니다.
                        """,
                example = "4"
        )
        int currentCount,

        @Schema(
                description = "현재 남아 있는 신청 가능 인원",
                example = "96"
        )
        int remainingCount,

        @Schema(
                description = """
                        신청 인원을 조회한 데이터 소스.

                        MYSQL:
                        Atomic / Pessimistic / Optimistic

                        REDIS:
                        Redis + MySQL 전략
                        """,
                example = "MYSQL",
                allowableValues = {
                        "MYSQL",
                        "REDIS"
                }
        )
        String countSource

) {
}