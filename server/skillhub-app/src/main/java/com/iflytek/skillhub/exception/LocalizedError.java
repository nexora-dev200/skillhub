package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

public interface LocalizedError {
    String messageCode();

    Object[] messageArgs();

    HttpStatus status();
}
