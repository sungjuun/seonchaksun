package com.seonchaksun.entry.service;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;
import com.seonchaksun.entry.metric.EntryMetrics;
import com.seonchaksun.entry.redis.RedisEventEntryService;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventEntryStrategyServiceTest {

    @Mock
    private EventEntryService atomicEventEntryService;

    @Mock
    private PessimisticEventEntryService pessimisticEventEntryService;

    @Mock
    private OptimisticEventEntryService optimisticEventEntryService;

    @Mock
    private RedisEventEntryService redisEventEntryService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EntryMetrics entryMetrics;

    @InjectMocks
    private EventEntryStrategyService eventEntryStrategyService;

    @Test
    @DisplayName("이벤트에 고정된 전략과 다른 전략으로 신청하면 거절한다")
    void rejectStrategyMismatch() {
        Event event = Event.create(
                "Redis 테스트",
                100,
                EntryStrategy.REDIS,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                LocalDateTime.of(2026, 8, 10, 18, 0)
        );

        when(eventRepository.findById(1L))
                .thenReturn(Optional.of(event));

        assertThatThrownBy(
                () -> eventEntryStrategyService.enter(
                        EntryStrategy.ATOMIC,
                        1L,
                        1001L
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.STRATEGY_MISMATCH
                                )
                );

        verify(atomicEventEntryService, never())
                .enter(1L, 1001L);

        verify(redisEventEntryService, never())
                .enter(1L, 1001L);
    }
}
