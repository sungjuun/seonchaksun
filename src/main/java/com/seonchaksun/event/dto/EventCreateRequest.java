package com.seonchaksun.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventCreateRequest(

        @NotBlank(message = "이벤트명은 필수입니다.")
        @Size(max = 100, message = "이벤트명은 100자 이하여야 합니다.")
        String name,

        @Positive(message = "이벤트 정원은 1명 이상이어야 합니다.")
        int capacity,

        @NotNull(message = "이벤트 시작 시간은 필수입니다.")
        LocalDateTime openAt,

        @NotNull(message = "이벤트 종료 시간은 필수입니다.")
        LocalDateTime closeAt
) {
}