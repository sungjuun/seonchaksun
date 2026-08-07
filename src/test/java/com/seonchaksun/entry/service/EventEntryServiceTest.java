package com.seonchaksun.entry.service;

import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventNotFoundException;
import com.seonchaksun.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventEntryServiceTest {

    private static final ZoneId ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-08-10T03:00:00Z");

    private static final LocalDateTime OPEN_AT =
            LocalDateTime.of(
                    2026, 8, 10, 10, 0
            );

    private static final LocalDateTime CLOSE_AT =
            LocalDateTime.of(
                    2026, 8, 10, 18, 0
            );

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventEntryRepository eventEntryRepository;

    private EventEntryService eventEntryService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZONE_ID
        );

        eventEntryService =
                new EventEntryService(
                        eventRepository,
                        eventEntryRepository,
                        clock
                );
    }

    @Test
    @DisplayName("이벤트에 정상적으로 신청한다")
    void enter() {
        // given
        Event event = createEvent();

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                1L,
                                1001L
                        )
        ).thenReturn(false);

        when(
                eventEntryRepository.save(
                        any(EventEntry.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        // when
        EventEntryResponse response =
                eventEntryService.enter(
                        1L,
                        1001L
                );

        // then
        assertThat(event.getCurrentCount())
                .isEqualTo(1);

        assertThat(response.userId())
                .isEqualTo(1001L);

        assertThat(response.createdAt())
                .isEqualTo(
                        LocalDateTime.of(
                                2026,
                                8,
                                10,
                                12,
                                0
                        )
                );

        verify(eventRepository)
                .findById(1L);

        verify(eventEntryRepository)
                .existsByEventIdAndUserId(
                        1L,
                        1001L
                );

        verify(eventEntryRepository)
                .save(any(EventEntry.class));
    }

    @Test
    @DisplayName("존재하지 않는 이벤트에는 신청할 수 없다")
    void cannotEnterMissingEvent() {
        // given
        when(eventRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> eventEntryService.enter(
                        999L,
                        1001L
                )
        )
                .isInstanceOf(
                        EventNotFoundException.class
                );

        verify(
                eventEntryRepository,
                never()
        ).save(any(EventEntry.class));
    }

    @Test
    @DisplayName("이미 신청한 사용자는 중복 신청할 수 없다")
    void cannotEnterDuplicate() {
        // given
        Event event = createEvent();

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                1L,
                                1001L
                        )
        ).thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> eventEntryService.enter(
                        1L,
                        1001L
                )
        )
                .isInstanceOf(
                        DuplicateEntryException.class
                );

        assertThat(event.getCurrentCount())
                .isZero();

        verify(
                eventEntryRepository,
                never()
        ).save(any(EventEntry.class));
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