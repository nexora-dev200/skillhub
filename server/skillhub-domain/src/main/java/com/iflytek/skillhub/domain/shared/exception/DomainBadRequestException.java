package com.iflytek.skillhub.domain.shared.exception;

public class DomainBadRequestException extends LocalizedDomainException {

    public DomainBadRequestException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }
}
