package com.seonchaksun.event.service;

import com.seonchaksun.entry.service.EntryStrategy;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.dto.EventCreateRequest;
import com.seonchaksun.event.dto.EventResponse;
import com.seonchaksun.event.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final LocalDateTime OPEN_AT =
            LocalDateTime.of(2026, 8, 10, 10, 0);

    private static final LocalDateTime CLOSE_AT =
            LocalDateTime.of(2026, 8, 10, 18, 0);

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("이벤트 생성 요청으로 이벤트를 저장한다")
    void createEvent() {
        // given
        EventCreateRequest request = new EventCreateRequest(
                "한정판 키보드 사전예약",
                100,
                OPEN_AT,
                CLOSE_AT
        );

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        EventResponse response =
                eventService.createEvent(request);

        // then
        ArgumentCaptor<Event> eventCaptor =
                ArgumentCaptor.forClass(Event.class);

        verify(eventRepository)
                .save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getName())
                .isEqualTo("한정판 키보드 사전예약");

        assertThat(savedEvent.getCapacity())
                .isEqualTo(100);

        assertThat(savedEvent.getCurrentCount())
                .isZero();

        assertThat(savedEvent.getStrategy())
                .isEqualTo(EntryStrategy.ATOMIC);

        assertThat(savedEvent.getOpenAt())
                .isEqualTo(OPEN_AT);

        assertThat(savedEvent.getCloseAt())
                .isEqualTo(CLOSE_AT);

        assertThat(response.id())
                .isNull();

        assertThat(response.name())
                .isEqualTo("한정판 키보드 사전예약");

        assertThat(response.capacity())
                .isEqualTo(100);

        assertThat(response.currentCount())
                .isZero();

        assertThat(response.strategy())
                .isEqualTo(EntryStrategy.ATOMIC);

        assertThat(response.openAt())
                .isEqualTo(OPEN_AT);

        assertThat(response.closeAt())
                .isEqualTo(CLOSE_AT);
    }

    @Test
    @DisplayName("이벤트 ID로 이벤트를 조회한다")
    void getEvent() {
        // given
        Event event = Event.create(
                "한정판 키보드 사전예약",
                100,
                OPEN_AT,
                CLOSE_AT
        );

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        // when
        EventResponse response =
                eventService.getEvent(1L);

        // then
        assertThat(response.name())
                .isEqualTo("한정판 키보드 사전예약");

        assertThat(response.capacity())
                .isEqualTo(100);

        assertThat(response.currentCount())
                .isZero();

        assertThat(response.strategy())
                .isEqualTo(EntryStrategy.ATOMIC);

        assertThat(response.openAt())
                .isEqualTo(OPEN_AT);

        assertThat(response.closeAt())
                .isEqualTo(CLOSE_AT);

        verify(eventRepository)
                .findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 이벤트를 조회하면 예외가 발생한다")
    void getEventNotFound() {
        // given
        when(eventRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> eventService.getEvent(999L)
        )
                .isInstanceOf(EventNotFoundException.class)
                .hasMessage("이벤트를 찾을 수 없습니다. eventId=999");

        verify(eventRepository)
                .findById(999L);
    }
}