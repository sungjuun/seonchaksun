package com.seonchaksun.entry.service;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;

import java.util.Locale;

public enum EntryStrategy {

    ATOMIC,
    PESSIMISTIC,
    OPTIMISTIC,
    REDIS;

    public static EntryStrategy from(
            String value
    ) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STRATEGY,
                    "신청 전략은 필수입니다."
            );
        }

        try {

            return EntryStrategy.valueOf(
                    value
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            throw new BusinessException(
                    ErrorCode.INVALID_STRATEGY,
                    "지원하지 않는 신청 전략입니다: "
                            + value
            );
        }
    }
}
