package com.seonchaksun.event.domain;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException(Long eventId) {
        super(
                ErrorCode.EVENT_NOT_FOUND,
                "이벤트를 찾을 수 없습니다. eventId=" + eventId
        );
    }
}