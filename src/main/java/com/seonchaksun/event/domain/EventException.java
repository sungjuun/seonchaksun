package com.seonchaksun.event.domain;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;

public class EventException extends BusinessException {

    public EventException(String message) {
        super(
                ErrorCode.INVALID_EVENT,
                message
        );
    }
}