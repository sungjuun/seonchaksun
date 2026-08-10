package com.seonchaksun.entry.redis;

import com.seonchaksun.TestcontainersConfiguration;
import com.seonchaksun.entry.domain.DuplicateEntryException;
import com.seonchaksun.entry.domain.EventEntry;
import com.seonchaksun.entry.repository.EventEntryRepository;
import com.seonchaksun.event.domain.Event;
import com.seonchaksun.event.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisEventEntryCompensationTest {

    private static final long USER_ID = 1L;

    @Autowired
    private RedisEventEntryService
            redisEventEntryService;

    @Autowired
    private RedisCapacityService
            redisCapacityService;

    @Autowired
    private EventRepository
            eventRepository;

    /*
     * EventEntry INSERT 실패 상황을
     * 의도적으로 만들기 위해 Mock으로 교체한다.
     */
    @MockitoBean
    private EventEntryRepository
            eventEntryRepository;

    @MockitoBean
    private Clock clock;

    private Long eventId;

    @AfterEach
    void tearDown() {

        if (eventId != null) {
            redisCapacityService.clear(
                    eventId
            );
        }
    }

    @Test
    @DisplayName(
            "DB 저장이 실패하면 Redis 예약을 보상한다"
    )
    void compensateWhenDatabaseSaveFails() {

        // given
        when(clock.instant())
                .thenReturn(
                        Instant.parse(
                                "2026-08-10T03:00:00Z"
                        )
                );

        when(clock.getZone())
                .thenReturn(
                        ZoneId.of(
                                "Asia/Seoul"
                        )
                );

        Event event =
                eventRepository.save(
                        Event.create(
                                "Redis 보상 테스트",
                                100,
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        10,
                                        10,
                                        0
                                ),
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        10,
                                        18,
                                        0
                                )
                        )
                );

        eventId =
                event.getId();

        /*
         * 중복 신청 사전 검사에서는
         * 아직 신청하지 않은 사용자로 처리한다.
         */
        when(
                eventEntryRepository
                        .existsByEventIdAndUserId(
                                eventId,
                                USER_ID
                        )
        )
                .thenReturn(false);

        /*
         * Redis 예약까지는 성공한 뒤,
         *
         * 실제 MySQL INSERT 단계에서
         * Constraint 오류가 발생한 상황을
         * 강제로 만든다.
         */
        when(
                eventEntryRepository
                        .saveAndFlush(
                                any(EventEntry.class)
                        )
        )
                .thenThrow(
                        new DataIntegrityViolationException(
                                "강제로 발생시킨 DB 저장 실패"
                        )
                );

        /*
         * 호출 전 Redis에는
         * 아무 예약도 없어야 한다.
         */
        assertThat(
                redisCapacityService
                        .getCurrentCount(
                                eventId
                        )
        )
                .isZero();

        // when & then
        assertThatThrownBy(
                () ->
                        redisEventEntryService.enter(
                                eventId,
                                USER_ID
                        )
        )
                .isInstanceOf(
                        DuplicateEntryException.class
                );

        /*
         * 처리 흐름:
         *
         * Redis
         * 0 → 1
         *
         * DB 저장 실패
         *
         * Redis release
         * 1 → 0
         *
         * 따라서 최종 Redis count는
         * 반드시 다시 0이어야 한다.
         */
        long redisCount =
                redisCapacityService
                        .getCurrentCount(
                                eventId
                        );

        System.out.println(
                "===================================="
        );

        System.out.println(
                "Redis 보상 처리 테스트 결과"
        );

        System.out.println(
                "------------------------------------"
        );

        System.out.println(
                "DB 저장 = 실패"
        );

        System.out.println(
                "Redis 예약 보상 후 count = "
                        + redisCount
        );

        System.out.println(
                "===================================="
        );

        assertThat(redisCount)
                .isZero();
    }
}