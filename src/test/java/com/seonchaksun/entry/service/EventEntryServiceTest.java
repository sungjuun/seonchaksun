package com.seonchaksun.entry.service;

import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.entry.dto.EventEntryResponse;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.domain.EventException;
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
                    2026,
                    8,
                    10,
                    10,
                    0
            );

    private static final LocalDateTime CLOSE_AT =
            LocalDateTime.of(
                    2026,
                    8,
                    10,
                    18,
                    0
            );

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventEntryRepository eventEntryRepository;

    private EventEntryService eventEntryService;

    @BeforeEach
    void setUp() {

        Clock clock =
                Clock.fixed(
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
    @DisplayName("Atomic Update로 이벤트에 정상 신청한다")
    void enter() {

        // given
        Event event =
                createEvent();

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                1L,
                                1001L
                        )
        ).thenReturn(false);

        when(
                eventRepository
                        .incrementCurrentCount(
                                1L,
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        10,
                                        12,
                                        0
                                )
                        )
        ).thenReturn(1);

        when(
                eventRepository
                        .getReferenceById(1L)
        ).thenReturn(event);

        when(
                eventEntryRepository
                        .saveAndFlush(
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
        assertThat(
                response.userId()
        ).isEqualTo(
                1001L
        );

        assertThat(
                response.createdAt()
        ).isEqualTo(
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        12,
                        0
                )
        );

        verify(
                eventRepository
        ).incrementCurrentCount(
                1L,
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        12,
                        0
                )
        );

        verify(
                eventEntryRepository
        ).saveAndFlush(
                any(EventEntry.class)
        );
    }

    @Test
    @DisplayName("이미 신청한 사용자는 중복 신청할 수 없다")
    void cannotEnterDuplicate() {

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                1L,
                                1001L
                        )
        ).thenReturn(true);

        assertThatThrownBy(
                () ->
                        eventEntryService.enter(
                                1L,
                                1001L
                        )
        )
                .isInstanceOf(
                        DuplicateEntryException.class
                );

        verify(
                eventRepository,
                never()
        ).incrementCurrentCount(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("존재하지 않는 이벤트에는 신청할 수 없다")
    void cannotEnterMissingEvent() {

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                999L,
                                1001L
                        )
        ).thenReturn(false);

        when(
                eventRepository
                        .incrementCurrentCount(
                                any(),
                                any()
                        )
        ).thenReturn(0);

        /*
         * Atomic Update 실패 사유를 판별할 때
         * 일반 findById()가 아니라
         * locking read를 사용하도록 변경되었기 때문에
         * 해당 메서드를 mock한다.
         */
        when(
                eventRepository
                        .findByIdWithPessimisticLock(
                                999L
                        )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        eventEntryService.enter(
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
        ).saveAndFlush(
                any(EventEntry.class)
        );
    }

    @Test
    @DisplayName("정원이 가득 차면 신청할 수 없다")
    void cannotEnterFullEvent() {

        Event event =
                Event.create(
                        "선착순 이벤트",
                        1,
                        OPEN_AT,
                        CLOSE_AT
                );

        /*
         * capacity = 1인 Event를
         * 미리 한 번 신청시켜서
         * 정원을 가득 채운 상태를 만든다.
         */
        event.enter(
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        11,
                        0
                )
        );

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                1L,
                                1002L
                        )
        ).thenReturn(false);

        /*
         * Atomic Update 실패 상황.
         *
         * DB에서는 이미
         * current_count == capacity라고 가정한다.
         */
        when(
                eventRepository
                        .incrementCurrentCount(
                                any(),
                                any()
                        )
        ).thenReturn(0);

        /*
         * 실패 원인 확인 시
         * 최신 상태를 locking read로 조회한다.
         */
        when(
                eventRepository
                        .findByIdWithPessimisticLock(
                                1L
                        )
        ).thenReturn(
                Optional.of(event)
        );

        assertThatThrownBy(
                () ->
                        eventEntryService.enter(
                                1L,
                                1002L
                        )
        )
                .isInstanceOf(
                        EventException.class
                )
                .hasMessage(
                        "이벤트 신청이 마감되었습니다."
                );

        verify(
                eventEntryRepository,
                never()
        ).saveAndFlush(
                any(EventEntry.class)
        );
    }

    @Test
    @DisplayName("DB UNIQUE 제약조건 위반은 중복 신청 예외로 변환한다")
    void convertsUniqueConstraintViolationToDuplicateEntryException() {

        // given
        Event event =
                createEvent();

        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                1L,
                                1001L
                        )
        ).thenReturn(false);

        when(
                eventRepository
                        .incrementCurrentCount(
                                1L,
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        10,
                                        12,
                                        0
                                )
                        )
        ).thenReturn(1);

        when(
                eventRepository
                        .getReferenceById(
                                1L
                        )
        ).thenReturn(event);

        when(
                eventEntryRepository
                        .saveAndFlush(
                                any(EventEntry.class)
                        )
        ).thenThrow(
                new org.springframework.dao
                        .DataIntegrityViolationException(
                        "Duplicate entry"
                )
        );

        // when & then
        assertThatThrownBy(
                () ->
                        eventEntryService.enter(
                                1L,
                                1001L
                        )
        )
                .isInstanceOf(
                        DuplicateEntryException.class
                );
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