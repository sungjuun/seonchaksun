package com.seonchaksun.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(
        description = "선착순 이벤트 생성 요청"
)
public record EventCreateRequest(

        @Schema(
                description = "이벤트 이름",
                example = "한정판 키보드 사전예약",
                maxLength = 100
        )
        @NotBlank(
                message = "이벤트명은 필수입니다."
        )
        @Size(
                max = 100,
                message = "이벤트명은 100자 이하여야 합니다."
        )
        String name,

        @Schema(
                description = "이벤트 최대 신청 가능 인원",
                example = "100",
                minimum = "1"
        )
        @Positive(
                message = "이벤트 정원은 1명 이상이어야 합니다."
        )
        int capacity,

        @Schema(
                description = "이벤트 신청 시작 시간",
                example = "2026-08-11T14:00:00"
        )
        @NotNull(
                message = "이벤트 시작 시간은 필수입니다."
        )
        LocalDateTime openAt,

        @Schema(
                description = "이벤트 신청 종료 시간",
                example = "2026-08-11T18:00:00"
        )
        @NotNull(
                message = "이벤트 종료 시간은 필수입니다."
        )
        LocalDateTime closeAt

) {
}