package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public class AccountPendingException extends OAuth2AuthenticationException {

    public AccountPendingException() {
        super(new OAuth2Error("account_pending", "Account pending approval", null));
    }
}
