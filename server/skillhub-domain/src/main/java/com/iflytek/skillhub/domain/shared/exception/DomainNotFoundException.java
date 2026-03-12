package com.iflytek.skillhub.domain.shared.exception;

public class DomainNotFoundException extends LocalizedDomainException {

    public DomainNotFoundException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }
}
