package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends LocalizedException {

    public UnauthorizedException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }
}
