package com.seonchaksun.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_EVENT(
            HttpStatus.BAD_REQUEST,
            "INVALID_EVENT",
            "잘못된 이벤트 요청입니다."
    ),

    INVALID_STRATEGY(
            HttpStatus.BAD_REQUEST,
            "INVALID_STRATEGY",
            "지원하지 않는 신청 전략입니다."
    ),

    STRATEGY_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "STRATEGY_MISMATCH",
            "이 이벤트에 지정된 신청 전략과 요청 전략이 다릅니다."
    ),

    EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "EVENT_NOT_FOUND",
            "이벤트를 찾을 수 없습니다."
    ),

    DUPLICATE_ENTRY(
            HttpStatus.CONFLICT,
            "DUPLICATE_ENTRY",
            "이미 신청한 이벤트입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(
            HttpStatus status,
            String code,
            String message
    ) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
