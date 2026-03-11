package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PostgresSearchRebuildService implements SearchRebuildService {

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SearchIndexService searchIndexService;

    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SearchIndexService searchIndexService) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.searchIndexService = searchIndexService;
    }

    @Override
    public void rebuildAll() {
        // This would need a findAll method in SkillRepository
        // For now, we'll leave it as a placeholder
        throw new UnsupportedOperationException("rebuildAll not yet implemented");
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        List<Skill> skills = skillRepository.findByNamespaceIdAndStatus(namespaceId, SkillStatus.ACTIVE);

        for (Skill skill : skills) {
            rebuildBySkill(skill.getId());
        }
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        Optional<Skill> skillOpt = skillRepository.findById(skillId);
        if (skillOpt.isEmpty()) {
            return;
        }

        Skill skill = skillOpt.get();
        Optional<Namespace> namespaceOpt = namespaceRepository.findById(skill.getNamespaceId());
        if (namespaceOpt.isEmpty()) {
            return;
        }

        Namespace namespace = namespaceOpt.get();

        String searchText = buildSearchText(skill);

        SkillSearchDocument document = new SkillSearchDocument(
                skill.getId(),
                skill.getNamespaceId(),
                namespace.getSlug(),
                skill.getOwnerId(),
                skill.getDisplayName() != null ? skill.getDisplayName() : skill.getSlug(),
                skill.getSummary(),
                "", // keywords - could be extracted from metadata
                searchText,
                skill.getVisibility().name(),
                skill.getStatus().name()
        );

        searchIndexService.index(document);
    }

    private String buildSearchText(Skill skill) {
        StringBuilder sb = new StringBuilder();
        if (skill.getDisplayName() != null) {
            sb.append(skill.getDisplayName()).append(" ");
        }
        sb.append(skill.getSlug()).append(" ");
        if (skill.getSummary() != null) {
            sb.append(skill.getSummary()).append(" ");
        }
        return sb.toString().trim();
    }
}
