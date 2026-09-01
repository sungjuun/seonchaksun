package com.seonchaksun.entry.service;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryStrategyTest {

    @Test
    @DisplayName("대소문자와 공백을 무시하고 신청 전략을 변환한다")
    void parseStrategy() {
        assertThat(
                EntryStrategy.from(" redis ")
        ).isEqualTo(
                EntryStrategy.REDIS
        );
    }

    @Test
    @DisplayName("지원하지 않는 전략이면 400 비즈니스 예외를 발생시킨다")
    void rejectInvalidStrategy() {
        assertThatThrownBy(
                () -> EntryStrategy.from("unknown")
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(
                                        ErrorCode.INVALID_STRATEGY
                                )
                );
    }
}
