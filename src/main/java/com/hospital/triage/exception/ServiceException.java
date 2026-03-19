package com.hospital.triage.exception;

import com.hospital.triage.common.enums.ErrorCodeEnum;
import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {

    private final String code;

    public ServiceException(String message) {
        this(ErrorCodeEnum.BAD_REQUEST.getCode(), message);
    }

    public ServiceException(ErrorCodeEnum errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    public ServiceException(String code, String message) {
        super(message);
        this.code = code;
    }
}
