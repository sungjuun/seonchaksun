package com.seonchaksun.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_EVENT(
            HttpStatus.BAD_REQUEST,
            "INVALID_EVENT",
            "잘못된 이벤트 요청입니다."
    ),

    EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "EVENT_NOT_FOUND",
            "이벤트를 찾을 수 없습니다."
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