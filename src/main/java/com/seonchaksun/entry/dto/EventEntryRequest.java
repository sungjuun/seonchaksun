package com.seonchaksun.entry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(
        description = "선착순 이벤트 신청 요청"
)
public record EventEntryRequest(

        @Schema(
                description = "이벤트에 신청할 사용자 ID",
                example = "1001",
                minimum = "1"
        )
        @NotNull(
                message = "사용자 ID는 필수입니다."
        )
        @Positive(
                message = "사용자 ID는 1 이상이어야 합니다."
        )
        Long userId

) {
}