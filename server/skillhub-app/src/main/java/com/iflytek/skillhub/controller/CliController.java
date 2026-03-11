package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cli")
public class CliController {

    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(Map.of(
            "data", Map.of(
                "userId", principal.userId(),
                "displayName", principal.displayName(),
                "email", principal.email() != null ? principal.email() : "",
                "avatarUrl", principal.avatarUrl() != null ? principal.avatarUrl() : "",
                "authType", principal.oauthProvider(),
                "platformRoles", principal.platformRoles()
            )
        ));
    }
}
