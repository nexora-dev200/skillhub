package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SkillSearchAppService {

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;

    public SkillSearchAppService(
            SearchQueryService searchQueryService,
            SkillRepository skillRepository) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
    }

    public record SearchResponse(
            List<SkillSummaryResponse> skills,
            long total,
            int page,
            int size
    ) {}

    public SearchResponse search(
            String keyword,
            Long namespaceId,
            String sortBy,
            int page,
            int size,
            Long userId,
            Map<Long, NamespaceRole> userNsRoles) {

        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);

        SearchQuery query = new SearchQuery(
                keyword,
                namespaceId,
                scope,
                sortBy != null ? sortBy : "newest",
                page,
                size
        );

        SearchResult result = searchQueryService.search(query);

        List<SkillSummaryResponse> skills = new ArrayList<>();
        for (Long skillId : result.skillIds()) {
            skillRepository.findById(skillId).ifPresent(skill -> {
                skills.add(toSummaryResponse(skill));
            });
        }

        return new SearchResponse(skills, result.total(), result.page(), result.size());
    }

    private SearchVisibilityScope buildVisibilityScope(Long userId, Map<Long, NamespaceRole> userNsRoles) {
        if (userId == null || userNsRoles == null) {
            return SearchVisibilityScope.anonymous();
        }

        Set<Long> memberNamespaceIds = userNsRoles.keySet();
        Set<Long> adminNamespaceIds = userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        return new SearchVisibilityScope(userId, memberNamespaceIds, adminNamespaceIds);
    }

    private SkillSummaryResponse toSummaryResponse(Skill skill) {
        // We need to get namespace slug and latest version
        // For now, we'll use placeholders
        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getDownloadCount(),
                null, // latestVersion - would need to query SkillVersion
                null  // namespace - would need to query Namespace
        );
    }
}
