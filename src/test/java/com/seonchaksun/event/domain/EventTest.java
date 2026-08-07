package com.seonchaksun.event.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final LocalDateTime OPEN_AT =
            LocalDateTime.of(2026, 8, 10, 10, 0);

    private static final LocalDateTime CLOSE_AT =
            LocalDateTime.of(2026, 8, 10, 18, 0);

    @Nested
    @DisplayName("이벤트 생성")
    class CreateEvent {

        @Test
        @DisplayName("정상적인 정보로 이벤트를 생성한다")
        void createEvent() {
            Event event = Event.create(
                    "한정판 키보드 사전예약",
                    100,
                    OPEN_AT,
                    CLOSE_AT
            );

            assertThat(event.getName()).isEqualTo("한정판 키보드 사전예약");
            assertThat(event.getCapacity()).isEqualTo(100);
            assertThat(event.getCurrentCount()).isZero();
            assertThat(event.getOpenAt()).isEqualTo(OPEN_AT);
            assertThat(event.getCloseAt()).isEqualTo(CLOSE_AT);
        }

        @Test
        @DisplayName("이벤트명이 비어 있으면 생성할 수 없다")
        void cannotCreateWithBlankName() {
            assertThatThrownBy(() -> Event.create(
                    " ",
                    100,
                    OPEN_AT,
                    CLOSE_AT
            ))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트명은 비어 있을 수 없습니다.");
        }

        @Test
        @DisplayName("정원이 1명 미만이면 생성할 수 없다")
        void cannotCreateWithInvalidCapacity() {
            assertThatThrownBy(() -> Event.create(
                    "한정판 키보드 사전예약",
                    0,
                    OPEN_AT,
                    CLOSE_AT
            ))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 정원은 1명 이상이어야 합니다.");
        }

        @Test
        @DisplayName("시작 시간이 종료 시간과 같으면 생성할 수 없다")
        void cannotCreateWhenOpenAtEqualsCloseAt() {
            assertThatThrownBy(() -> Event.create(
                    "한정판 키보드 사전예약",
                    100,
                    OPEN_AT,
                    OPEN_AT
            ))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 시작 시간은 종료 시간보다 빨라야 합니다.");
        }

        @Test
        @DisplayName("시작 시간이 종료 시간보다 늦으면 생성할 수 없다")
        void cannotCreateWhenOpenAtIsAfterCloseAt() {
            assertThatThrownBy(() -> Event.create(
                    "한정판 키보드 사전예약",
                    100,
                    CLOSE_AT,
                    OPEN_AT
            ))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 시작 시간은 종료 시간보다 빨라야 합니다.");
        }
    }

    @Nested
    @DisplayName("이벤트 신청")
    class EnterEvent {

        @Test
        @DisplayName("신청 기간이고 정원이 남아 있으면 신청 인원을 증가시킨다")
        void enterEvent() {
            Event event = createEvent(100);

            LocalDateTime now =
                    LocalDateTime.of(2026, 8, 10, 12, 0);

            event.enter(now);

            assertThat(event.getCurrentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이벤트 시작 시간에도 신청할 수 있다")
        void enterAtOpenTime() {
            Event event = createEvent(100);

            event.enter(OPEN_AT);

            assertThat(event.getCurrentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이벤트 시작 전에는 신청할 수 없다")
        void cannotEnterBeforeOpen() {
            Event event = createEvent(100);

            LocalDateTime beforeOpen = OPEN_AT.minusSeconds(1);

            assertThatThrownBy(() -> event.enter(beforeOpen))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 신청 기간이 아닙니다.");
        }

        @Test
        @DisplayName("이벤트 종료 시간에는 신청할 수 없다")
        void cannotEnterAtCloseTime() {
            Event event = createEvent(100);

            assertThatThrownBy(() -> event.enter(CLOSE_AT))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 신청 기간이 아닙니다.");
        }

        @Test
        @DisplayName("이벤트 종료 후에는 신청할 수 없다")
        void cannotEnterAfterClose() {
            Event event = createEvent(100);

            LocalDateTime afterClose = CLOSE_AT.plusSeconds(1);

            assertThatThrownBy(() -> event.enter(afterClose))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 신청 기간이 아닙니다.");
        }

        @Test
        @DisplayName("신청 인원이 정원과 같으면 신청할 수 없다")
        void cannotEnterWhenEventIsFull() {
            Event event = createEvent(1);

            LocalDateTime now =
                    LocalDateTime.of(2026, 8, 10, 12, 0);

            event.enter(now);

            assertThatThrownBy(() -> event.enter(now))
                    .isInstanceOf(EventException.class)
                    .hasMessage("이벤트 신청이 마감되었습니다.");

            assertThat(event.getCurrentCount()).isEqualTo(1);
        }
    }

    private Event createEvent(int capacity) {
        return Event.create(
                "한정판 키보드 사전예약",
                capacity,
                OPEN_AT,
                CLOSE_AT
        );
    }
}