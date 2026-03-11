package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {
        if (exception instanceof AccountPendingException) {
            getRedirectStrategy().sendRedirect(request, response, "/pending-approval");
            return;
        }
        if (exception instanceof AccountDisabledException) {
            getRedirectStrategy().sendRedirect(request, response, "/access-denied");
            return;
        }
        if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth2Exception
                && "access_denied".equals(oauth2Exception.getError().getErrorCode())) {
            getRedirectStrategy().sendRedirect(request, response, "/access-denied");
            return;
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
