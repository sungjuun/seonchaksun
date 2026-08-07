package com.seonchaksun.entry.domain;

import com.seonchaksun.event.domain.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEntryTest {

    private static final LocalDateTime OPEN_AT =
            LocalDateTime.of(2026, 8, 10, 10, 0);

    private static final LocalDateTime CLOSE_AT =
            LocalDateTime.of(2026, 8, 10, 18, 0);

    private static final LocalDateTime ENTRY_AT =
            LocalDateTime.of(2026, 8, 10, 12, 0);

    @Test
    @DisplayName("이벤트 신청 내역을 생성한다")
    void createEventEntry() {
        Event event = createEvent();

        EventEntry entry = EventEntry.create(
                event,
                1001L,
                ENTRY_AT
        );

        assertThat(entry.getEvent())
                .isEqualTo(event);

        assertThat(entry.getUserId())
                .isEqualTo(1001L);

        assertThat(entry.getCreatedAt())
                .isEqualTo(ENTRY_AT);
    }

    @Test
    @DisplayName("이벤트가 없으면 신청 내역을 생성할 수 없다")
    void cannotCreateWithoutEvent() {
        assertThatThrownBy(
                () -> EventEntry.create(
                        null,
                        1001L,
                        ENTRY_AT
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("이벤트는 필수입니다.");
    }

    @Test
    @DisplayName("사용자 ID가 1보다 작으면 신청 내역을 생성할 수 없다")
    void cannotCreateWithInvalidUserId() {
        Event event = createEvent();

        assertThatThrownBy(
                () -> EventEntry.create(
                        event,
                        0L,
                        ENTRY_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "사용자 ID는 1 이상이어야 합니다."
                );
    }

    @Test
    @DisplayName("신청 시간이 없으면 신청 내역을 생성할 수 없다")
    void cannotCreateWithoutCreatedAt() {
        Event event = createEvent();

        assertThatThrownBy(
                () -> EventEntry.create(
                        event,
                        1001L,
                        null
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("신청 시간은 필수입니다.");
    }

    private Event createEvent() {
        return Event.create(
                "한정판 키보드 사전예약",
                100,
                OPEN_AT,
                CLOSE_AT
        );
    }
}