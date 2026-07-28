package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliRestrictedReadAuthorizationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired NamespaceRepository namespaceRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired SkillVersionRepository skillVersionRepository;

    private String namespaceSlug;
    private String skillSlug;
    private String version;
    private String ownerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String ownerId = "private-owner-" + suffix;
        String outsiderId = "private-outsider-" + suffix;
        namespaceSlug = "private-ns-" + suffix;
        skillSlug = "private-skill-" + suffix;
        version = "1.0.0";

        userAccountRepository.save(new UserAccount(
                ownerId, "Private Skill Owner", ownerId + "@example.com", ""));
        userAccountRepository.save(new UserAccount(
                outsiderId, "Private Skill Outsider", outsiderId + "@example.com", ""));
        ownerToken = apiTokenService.createToken(
                ownerId, "owner-token-" + suffix, "[\"skill:read\"]").rawToken();
        outsiderToken = apiTokenService.createToken(
                outsiderId, "outsider-token-" + suffix, "[\"skill:read\"]").rawToken();

        Namespace namespace = namespaceRepository.save(
                new Namespace(namespaceSlug, "Private Namespace", ownerId));
        Skill skill = skillRepository.save(new Skill(
                namespace.getId(), skillSlug, ownerId, SkillVisibility.PRIVATE));
        SkillVersion published = new SkillVersion(skill.getId(), version, ownerId);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        published.setPublishedAt(Instant.parse("2026-07-28T00:00:00Z"));
        published.setDownloadReady(true);
        published = skillVersionRepository.save(published);
        skill.setLatestVersionId(published.getId());
        skillRepository.save(skill);
        skillRepository.flush();
        skillVersionRepository.flush();
    }

    @Test
    void outsiderCannotResolvePrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/resolve", namespaceSlug, skillSlug),
                        outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void outsiderCannotDownloadLatestPrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/download", namespaceSlug, skillSlug),
                        outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void outsiderCannotDownloadVersionedPrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download",
                                namespaceSlug, skillSlug, version),
                        outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ownerCanResolvePrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/resolve", namespaceSlug, skillSlug),
                        ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(skillSlug));
    }

    private MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder request,
            String rawToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
    }
}
