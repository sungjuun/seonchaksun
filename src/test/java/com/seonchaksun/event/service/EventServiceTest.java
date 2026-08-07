package com.seonchaksun.event.service;

import com.seonchaksun.event.domain.Event;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("이벤트 생성 요청으로 이벤트를 저장한다")
    void createEvent() {
        LocalDateTime openAt =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        LocalDateTime closeAt =
                LocalDateTime.of(2026, 8, 10, 18, 0);

        EventCreateRequest request = new EventCreateRequest(
                "한정판 키보드 사전예약",
                100,
                openAt,
                closeAt
        );

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.createEvent(request);

        ArgumentCaptor<Event> eventCaptor =
                ArgumentCaptor.forClass(Event.class);

        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getName())
                .isEqualTo("한정판 키보드 사전예약");
        assertThat(savedEvent.getCapacity()).isEqualTo(100);
        assertThat(savedEvent.getCurrentCount()).isZero();
        assertThat(savedEvent.getOpenAt()).isEqualTo(openAt);
        assertThat(savedEvent.getCloseAt()).isEqualTo(closeAt);

        assertThat(response.id()).isNull();
        assertThat(response.name())
                .isEqualTo("한정판 키보드 사전예약");
        assertThat(response.capacity()).isEqualTo(100);
    }
}