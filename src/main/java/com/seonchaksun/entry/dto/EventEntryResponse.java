package com.seonchaksun.entry.dto;

import com.seonchaksun.entry.domain.EventEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
        description = "선착순 이벤트 신청 결과"
)
public record EventEntryResponse(

        @Schema(
                description = "생성된 이벤트 신청 ID",
                example = "125"
        )
        Long id,

        @Schema(
                description = "신청한 이벤트 ID",
                example = "15"
        )
        Long eventId,

        @Schema(
                description = "신청 사용자 ID",
                example = "1001"
        )
        Long userId,

        @Schema(
                description = "신청이 생성된 시간",
                example = "2026-08-11T09:50:30"
        )
        LocalDateTime createdAt

) {

    public static EventEntryResponse from(
            EventEntry entry
    ) {

        return new EventEntryResponse(
                entry.getId(),
                entry.getEvent().getId(),
                entry.getUserId(),
                entry.getCreatedAt()
        );
    }
}