package com.seonchaksun.common.exception;

public record ApiErrorResponse(
        String code,
        String message
) {

    public static ApiErrorResponse from(
            BusinessException exception
    ) {
        return new ApiErrorResponse(
                exception.getErrorCode().getCode(),
                exception.getMessage()
        );
    }
}