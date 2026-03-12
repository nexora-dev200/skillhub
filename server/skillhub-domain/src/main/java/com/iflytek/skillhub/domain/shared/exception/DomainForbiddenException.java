package com.iflytek.skillhub.domain.shared.exception;

public class DomainForbiddenException extends LocalizedDomainException {

    public DomainForbiddenException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }
}
