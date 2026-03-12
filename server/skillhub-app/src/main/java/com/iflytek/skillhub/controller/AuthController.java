package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.AuthProviderResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseApiController {

    public AuthController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeResponse> me(@AuthenticationPrincipal PlatformPrincipal principal,
                                          Authentication authentication) {
        if (principal == null || authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("error.auth.required");
        }
        return ok("response.success.read", AuthMeResponse.from(principal));
    }

    @GetMapping("/providers")
    public ApiResponse<List<AuthProviderResponse>> providers() {
        var github = new AuthProviderResponse("github", "GitHub", "/oauth2/authorization/github");
        return ok("response.success.read", List.of(github));
    }
}
