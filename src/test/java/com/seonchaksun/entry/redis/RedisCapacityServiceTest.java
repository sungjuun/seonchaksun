package com.seonchaksun.entry.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisCapacityServiceTest {

    private static final Long EVENT_ID = 999L;

    @Autowired
    private RedisCapacityService redisCapacityService;

    @AfterEach
    void tearDown() {

        /*
         * 각 테스트가 끝난 뒤 Redis 데이터를 제거한다.
         *
         * 테스트 간 데이터가 서로 영향을 주지 않도록
         * 항상 초기화한다.
         */
        redisCapacityService.clear(
                EVENT_ID
        );
    }

    @Test
    @DisplayName(
            "Redis에서 이벤트 정원까지만 예약할 수 있다"
    )
    void reserve() {

        // given
        int capacity = 3;

        // when
        boolean first =
                redisCapacityService.reserve(
                        EVENT_ID,
                        capacity
                );

        boolean second =
                redisCapacityService.reserve(
                        EVENT_ID,
                        capacity
                );

        boolean third =
                redisCapacityService.reserve(
                        EVENT_ID,
                        capacity
                );

        boolean fourth =
                redisCapacityService.reserve(
                        EVENT_ID,
                        capacity
                );

        // then
        assertThat(first)
                .isTrue();

        assertThat(second)
                .isTrue();

        assertThat(third)
                .isTrue();

        /*
         * 정원이 3명이므로
         * 네 번째 신청은 실패해야 한다.
         */
        assertThat(fourth)
                .isFalse();

        /*
         * Redis에 저장된 최종 예약 수 역시
         * 정원인 3이어야 한다.
         */
        assertThat(
                redisCapacityService
                        .getCurrentCount(
                                EVENT_ID
                        )
        )
                .isEqualTo(3);
    }

    @Test
    @DisplayName(
            "Redis에서 예약한 자리를 다시 반환할 수 있다"
    )
    void release() {

        // given
        int capacity = 3;

        boolean reserved =
                redisCapacityService.reserve(
                        EVENT_ID,
                        capacity
                );

        assertThat(reserved)
                .isTrue();

        assertThat(
                redisCapacityService
                        .getCurrentCount(
                                EVENT_ID
                        )
        )
                .isEqualTo(1);

        // when
        boolean released =
                redisCapacityService.release(
                        EVENT_ID
                );

        // then
        assertThat(released)
                .isTrue();

        /*
         * 예약 1건을 반환했으므로
         * 다시 0이 되어야 한다.
         */
        assertThat(
                redisCapacityService
                        .getCurrentCount(
                                EVENT_ID
                        )
        )
                .isZero();
    }

    @Test
    @DisplayName(
            "Redis 예약 수는 0보다 작아지지 않는다"
    )
    void cannotReleaseBelowZero() {

        // given
        /*
         * Redis에 아무 예약도 없는 상태다.
         *
         * currentCount = 0
         */

        // when
        boolean released =
                redisCapacityService.release(
                        EVENT_ID
                );

        // then
        /*
         * 반환할 예약이 없으므로
         * release는 실패해야 한다.
         */
        assertThat(released)
                .isFalse();

        /*
         * DECR에 의해 -1이 되어서는 안 된다.
         */
        assertThat(
                redisCapacityService
                        .getCurrentCount(
                                EVENT_ID
                        )
        )
                .isZero();
    }
}