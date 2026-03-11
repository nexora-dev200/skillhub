package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SkillQueryService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final ObjectStorageService objectStorageService;
    private final VisibilityChecker visibilityChecker;

    public SkillQueryService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            ObjectStorageService objectStorageService,
            VisibilityChecker visibilityChecker) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
    }

    public record SkillDetailDTO(
            Long id,
            String slug,
            String displayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            String latestVersion,
            Long namespaceId
    ) {}

    public SkillDetailDTO getSkillDetail(
            String namespaceSlug,
            String skillSlug,
            Long currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new SecurityException("Access denied to skill: " + skillSlug);
        }

        String latestVersion = null;
        if (skill.getLatestVersionId() != null) {
            SkillVersion version = skillVersionRepository.findById(skill.getLatestVersionId()).orElse(null);
            if (version != null) {
                latestVersion = version.getVersion();
            }
        }

        return new SkillDetailDTO(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getVisibility().name(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                latestVersion,
                skill.getNamespaceId()
        );
    }

    public Page<Skill> listSkillsByNamespace(
            String namespaceSlug,
            Long currentUserId,
            Map<Long, NamespaceRole> userNsRoles,
            Pageable pageable) {

        Namespace namespace = findNamespace(namespaceSlug);
        List<Skill> allSkills = skillRepository.findByNamespaceIdAndStatus(namespace.getId(), SkillStatus.ACTIVE);

        // Filter by visibility
        List<Skill> accessibleSkills = allSkills.stream()
                .filter(skill -> visibilityChecker.canAccess(skill, currentUserId, userNsRoles))
                .collect(Collectors.toList());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), accessibleSkills.size());
        List<Skill> pageContent = accessibleSkills.subList(start, end);

        return new PageImpl<>(pageContent, pageable, accessibleSkills.size());
    }

    public List<SkillFile> listFiles(String namespaceSlug, String skillSlug, String version) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        SkillVersion skillVersion = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + version));

        return skillFileRepository.findByVersionId(skillVersion.getId());
    }

    public InputStream getFileContent(String namespaceSlug, String skillSlug, String version, String filePath) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        SkillVersion skillVersion = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + version));

        List<SkillFile> files = skillFileRepository.findByVersionId(skillVersion.getId());
        SkillFile file = files.stream()
                .filter(f -> f.getFilePath().equals(filePath))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + filePath));

        return objectStorageService.getObject(file.getStorageKey());
    }

    public Page<SkillVersion> listVersions(String namespaceSlug, String skillSlug, Pageable pageable) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        List<SkillVersion> publishedVersions = skillVersionRepository.findBySkillIdAndStatus(
                skill.getId(), SkillVersionStatus.PUBLISHED);

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), publishedVersions.size());
        List<SkillVersion> pageContent = publishedVersions.subList(start, end);

        return new PageImpl<>(pageContent, pageable, publishedVersions.size());
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + slug));
    }
}
