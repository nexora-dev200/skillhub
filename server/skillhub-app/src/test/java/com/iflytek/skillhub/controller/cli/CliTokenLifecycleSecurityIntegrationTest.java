package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliTokenLifecycleSecurityIntegrationTest {

    private enum InvalidCredentialState {
        REVOKED,
        EXPIRED,
        UNKNOWN,
        EMPTY,
        MALFORMED
    }

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired ApiTokenRepository apiTokenRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired Clock clock;
    @MockBean CliSkillAppService cliSkillAppService;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = "token-matrix-" + UUID.randomUUID();
        userAccountRepository.save(new UserAccount(
                userId, "Token Matrix", userId + "@example.com", ""));
        given(cliSkillAppService.search(any(), anyInt(), any(), any()))
                .willReturn(new CliSkillAppService.CliSearchResult(List.of(), 0, 20));
        given(cliSkillAppService.resolve(anyString(), anyString(), any(), any(), any()))
                .willReturn(new CliResolveResponse(
                        "global", "demo", "1.0.0", 1L, "sha256:empty",
                        "/api/v1/skills/global/demo/versions/1.0.0/download"));
        given(cliSkillAppService.downloadLatest(anyString(), anyString(), any()))
                .willAnswer(ignored -> downloadResponse());
        given(cliSkillAppService.downloadVersion(anyString(), anyString(), anyString(), any()))
                .willAnswer(ignored -> downloadResponse());
    }

    @Test
    void whoamiWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/cli/v1/auth/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void whoamiWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(get("/api/cli/v1/auth/whoami"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value(userId));
    }

    @ParameterizedTest(name = "whoami rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void whoamiRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        mockMvc.perform(withInvalidBearer(get("/api/cli/v1/auth/whoami"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void searchWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void searchWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"), token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "search rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void searchRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        mockMvc.perform(withInvalidBearer(
                        get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void resolveWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/global/demo/resolve"))
                .andExpect(status().isOk());
    }

    @Test
    void resolveWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(get("/api/cli/v1/skills/global/demo/resolve"), token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "resolve rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void resolveRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        mockMvc.perform(withInvalidBearer(get("/api/cli/v1/skills/global/demo/resolve"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void latestDownloadWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/global/demo/download"))
                .andExpect(status().isOk());
    }

    @Test
    void latestDownloadWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(get("/api/cli/v1/skills/global/demo/download"), token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @ParameterizedTest(name = "latest download rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void latestDownloadRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        mockMvc.perform(withInvalidBearer(get("/api/cli/v1/skills/global/demo/download"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void versionedDownloadWithoutAuthorizationReturns200() throws Exception {
        mockMvc.perform(get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"))
                .andExpect(status().isOk());
    }

    @Test
    void versionedDownloadWithValidPersistedTokenReturns200() throws Exception {
        String token = createActiveToken();
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @ParameterizedTest(name = "versioned download rejects {0}")
    @EnumSource(InvalidCredentialState.class)
    void versionedDownloadRejectsInvalidBearer(InvalidCredentialState state) throws Exception {
        clearInvocations(cliSkillAppService);
        mockMvc.perform(withInvalidBearer(
                        get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(cliSkillAppService);
    }

    @Test
    void sameRawTokenIsRejectedByAllEndpointsAfterValidUseAndRevocation() throws Exception {
        ApiTokenService.TokenCreateResult token = createToken();
        String rawToken = token.rawToken();

        assertSuccessEnvelope(withBearer(get("/api/cli/v1/auth/whoami"), rawToken))
                .andExpect(jsonPath("$.data.handle").value(userId));
        assertSuccessEnvelope(withBearer(
                get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"),
                rawToken));
        assertSuccessEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/resolve"), rawToken));
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/global/demo/download"), rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));

        apiTokenService.revokeToken(token.entity().getId(), userId);
        clearInvocations(cliSkillAppService);

        assertUnauthorizedEnvelope(withBearer(get("/api/cli/v1/auth/whoami"), rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/search").param("q", "demo").param("limit", "20"),
                rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/resolve"), rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/download"), rawToken));
        assertUnauthorizedEnvelope(withBearer(
                get("/api/cli/v1/skills/global/demo/versions/1.0.0/download"), rawToken));
        verifyNoInteractions(cliSkillAppService);
    }

    private ResultActions assertSuccessEnvelope(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").isString())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    private void assertUnauthorizedEnvelope(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").isString())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    private MockHttpServletRequestBuilder withInvalidBearer(
            MockHttpServletRequestBuilder request,
            InvalidCredentialState state) {
        return request
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader(state))
                .with(authentication(sessionAuthentication()));
    }

    private MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder request,
            String rawToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
    }

    private String authorizationHeader(InvalidCredentialState state) {
        return switch (state) {
            case REVOKED -> {
                ApiTokenService.TokenCreateResult result = createToken();
                apiTokenService.revokeToken(result.entity().getId(), userId);
                yield "Bearer " + result.rawToken();
            }
            case EXPIRED -> {
                ApiTokenService.TokenCreateResult result = createToken();
                ApiToken token = result.entity();
                token.setExpiresAt(Instant.now(clock).minusSeconds(1));
                apiTokenRepository.saveAndFlush(token);
                yield "Bearer " + result.rawToken();
            }
            case UNKNOWN -> "Bearer sk_unknown_" + UUID.randomUUID();
            case EMPTY -> "Bearer ";
            case MALFORMED -> "Bearer";
        };
    }

    private String createActiveToken() {
        return createToken().rawToken();
    }

    private ApiTokenService.TokenCreateResult createToken() {
        return apiTokenService.createToken(
                userId, "matrix-" + UUID.randomUUID(), "[\"skill:read\"]");
    }

    private UsernamePasswordAuthenticationToken sessionAuthentication() {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, "Session User", userId + "@example.com", "", "session", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private ResponseEntity<InputStreamResource> downloadResponse() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(
                        new ByteArrayInputStream("zip".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
