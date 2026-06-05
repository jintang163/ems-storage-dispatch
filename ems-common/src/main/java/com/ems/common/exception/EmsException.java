package com.ems.common.exception;

import lombok.Getter;

@Getter
public class EmsException extends RuntimeException {

    private final Integer code;

    public EmsException(String message) {
        super(message);
        this.code = 500;
    }

    public EmsException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public EmsException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }
}
