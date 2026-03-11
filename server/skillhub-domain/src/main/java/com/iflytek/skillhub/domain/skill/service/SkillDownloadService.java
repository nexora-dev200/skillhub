package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.ObjectMetadata;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

@Service
public class SkillDownloadService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillTagRepository skillTagRepository;
    private final ObjectStorageService objectStorageService;
    private final VisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher eventPublisher;

    public SkillDownloadService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillTagRepository skillTagRepository,
            ObjectStorageService objectStorageService,
            VisibilityChecker visibilityChecker,
            ApplicationEventPublisher eventPublisher) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillTagRepository = skillTagRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
        this.eventPublisher = eventPublisher;
    }

    public record DownloadResult(
            InputStream content,
            String filename,
            long contentLength,
            String contentType
    ) {}

    public DownloadResult downloadLatest(
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

        if (skill.getLatestVersionId() == null) {
            throw new IllegalArgumentException("No published version available for skill: " + skillSlug);
        }

        SkillVersion version = skillVersionRepository.findById(skill.getLatestVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Latest version not found"));

        return downloadVersion(skill, version);
    }

    public DownloadResult downloadVersion(
            String namespaceSlug,
            String skillSlug,
            String versionStr,
            Long currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new SecurityException("Access denied to skill: " + skillSlug);
        }

        SkillVersion version = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), versionStr)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionStr));

        return downloadVersion(skill, version);
    }

    public DownloadResult downloadByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            Long currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new SecurityException("Access denied to skill: " + skillSlug);
        }

        SkillTag tag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagName));

        if (tag.getVersionId() == null) {
            throw new IllegalArgumentException("Tag does not point to a version: " + tagName);
        }

        SkillVersion version = skillVersionRepository.findById(tag.getVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Version not found for tag: " + tagName));

        return downloadVersion(skill, version);
    }

    private DownloadResult downloadVersion(Skill skill, SkillVersion version) {
        String storageKey = String.format("packages/%d/%d/bundle.zip", skill.getId(), version.getId());

        if (!objectStorageService.exists(storageKey)) {
            throw new IllegalArgumentException("Bundle not found in storage");
        }

        ObjectMetadata metadata = objectStorageService.getMetadata(storageKey);
        InputStream content = objectStorageService.getObject(storageKey);

        // Publish download event
        eventPublisher.publishEvent(new SkillDownloadedEvent(skill.getId(), version.getId()));

        String filename = String.format("%s-%s.zip", skill.getSlug(), version.getVersion());

        return new DownloadResult(content, filename, metadata.size(), metadata.contentType());
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + slug));
    }
}
