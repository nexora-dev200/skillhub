package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubSkillItem;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.service.SkillSearchAppService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compat/v1")
public class ClawHubCompatController {

    private final CanonicalSlugMapper mapper;
    private final SkillSearchAppService skillSearchAppService;

    public ClawHubCompatController(CanonicalSlugMapper mapper, SkillSearchAppService skillSearchAppService) {
        this.mapper = mapper;
        this.skillSearchAppService = skillSearchAppService;
    }

    @GetMapping("/search")
    public ClawHubSearchResponse search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q,
                null,
                q == null || q.isBlank() ? "newest" : "relevance",
                page,
                limit,
                userId,
                userNsRoles
        );

        List<ClawHubSkillItem> items = response.items().stream()
                .map(item -> new ClawHubSkillItem(
                        mapper.toCanonical(item.namespace(), item.slug()),
                        item.summary(),
                        item.latestVersion(),
                        item.starCount()))
                .toList();

        return new ClawHubSearchResponse(items);
    }

    @GetMapping("/resolve/{canonicalSlug}")
    public ClawHubResolveResponse resolve(
            @PathVariable String canonicalSlug,
            @RequestParam(defaultValue = "latest") String version) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        return new ClawHubResolveResponse(
                canonicalSlug,
                version,
                "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/download"
        );
    }

    @GetMapping("/whoami")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        return new ClawHubWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.email()
        );
    }
}
