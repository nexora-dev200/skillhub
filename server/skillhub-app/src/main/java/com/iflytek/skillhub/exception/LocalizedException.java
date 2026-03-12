package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

public abstract class LocalizedException extends RuntimeException implements LocalizedError {

    private final String messageCode;
    private final Object[] messageArgs;

    protected LocalizedException(String messageCode, Object... messageArgs) {
        super(messageCode);
        this.messageCode = messageCode;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageCode() {
        return messageCode;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }

    @Override
    public abstract HttpStatus status();
}
