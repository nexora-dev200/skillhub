package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whoamiShouldReturnUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/cli/whoami"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void whoamiShouldReturnCurrentPrincipal() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            7L,
            "cli-user",
            "cli@example.com",
            "",
            "api_token",
            Set.of("SKILL_ADMIN")
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/cli/whoami").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(7))
            .andExpect(jsonPath("$.data.displayName").value("cli-user"))
            .andExpect(jsonPath("$.data.authType").value("api_token"))
            .andExpect(jsonPath("$.data.platformRoles[0]").value("SKILL_ADMIN"));
    }
}
