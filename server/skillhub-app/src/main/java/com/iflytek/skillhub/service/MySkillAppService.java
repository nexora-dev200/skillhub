package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MySkillAppService {

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;

    public MySkillAppService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
    }

    public List<SkillSummaryResponse> listMySkills(String userId) {
        List<Skill> skills = skillRepository.findByOwnerId(userId).stream()
                .sorted(Comparator.comparing(Skill::getUpdatedAt).reversed())
                .toList();

        List<Long> latestVersionIds = skills.stream()
                .map(Skill::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versionsById = latestVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(latestVersionIds).stream()
                        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

        List<Long> namespaceIds = skills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, String> namespaceSlugsById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(
                                com.iflytek.skillhub.domain.namespace.Namespace::getId,
                                com.iflytek.skillhub.domain.namespace.Namespace::getSlug));

        return skills.stream()
                .map(skill -> toSummaryResponse(skill, versionsById, namespaceSlugsById))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, SkillVersion> versionsById,
            Map<Long, String> namespaceSlugsById) {
        String latestVersion = skill.getLatestVersionId() == null
                ? null
                : Optional.ofNullable(versionsById.get(skill.getLatestVersionId()))
                        .map(SkillVersion::getVersion)
                        .orElse(null);

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                latestVersion,
                namespaceSlugsById.get(skill.getNamespaceId()),
                skill.getUpdatedAt()
        );
    }
}
