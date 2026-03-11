package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal PlatformPrincipal principal,
                                                  Authentication authentication) {
        if (principal == null || authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of(
            "userId", principal.userId(),
            "displayName", principal.displayName(),
            "email", principal.email() != null ? principal.email() : "",
            "avatarUrl", principal.avatarUrl() != null ? principal.avatarUrl() : "",
            "oauthProvider", principal.oauthProvider(),
            "platformRoles", principal.platformRoles()
        ));
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> providers() {
        var github = Map.of(
            "id", "github",
            "name", "GitHub",
            "authorizationUrl", "/oauth2/authorization/github"
        );
        return ResponseEntity.ok(Map.of("data", List.of(github)));
    }
}
