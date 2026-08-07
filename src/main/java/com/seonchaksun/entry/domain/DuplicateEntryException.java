package com.seonchaksun.entry.domain;

import com.seonchaksun.common.exception.BusinessException;
import com.seonchaksun.common.exception.ErrorCode;

public class DuplicateEntryException
        extends BusinessException {

    public DuplicateEntryException(
            Long eventId,
            Long userId
    ) {
        super(
                ErrorCode.DUPLICATE_ENTRY,
                "이미 신청한 이벤트입니다. eventId="
                        + eventId
                        + ", userId="
                        + userId
        );
    }
}