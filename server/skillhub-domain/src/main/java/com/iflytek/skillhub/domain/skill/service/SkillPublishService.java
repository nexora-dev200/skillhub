package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class SkillPublishService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final ObjectStorageService objectStorageService;
    private final SkillPackageValidator skillPackageValidator;
    private final SkillMetadataParser skillMetadataParser;
    private final PrePublishValidator prePublishValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public SkillPublishService(
            NamespaceRepository namespaceRepository,
            NamespaceMemberRepository namespaceMemberRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            ObjectStorageService objectStorageService,
            SkillPackageValidator skillPackageValidator,
            SkillMetadataParser skillMetadataParser,
            PrePublishValidator prePublishValidator,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.objectStorageService = objectStorageService;
        this.skillPackageValidator = skillPackageValidator;
        this.skillMetadataParser = skillMetadataParser;
        this.prePublishValidator = prePublishValidator;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SkillVersion publishFromEntries(
            String namespaceSlug,
            List<PackageEntry> entries,
            Long publisherId,
            SkillVisibility visibility) {

        // 1. Find namespace by slug
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + namespaceSlug));

        // 2. Check publisher is member
        namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), publisherId)
                .orElseThrow(() -> new IllegalArgumentException("Publisher is not a member of namespace: " + namespaceSlug));

        // 3. Validate package
        ValidationResult packageValidation = skillPackageValidator.validate(entries);
        if (!packageValidation.passed()) {
            throw new IllegalArgumentException("Package validation failed: " + String.join(", ", packageValidation.errors()));
        }

        // 4. Parse SKILL.md
        PackageEntry skillMd = entries.stream()
                .filter(e -> e.path().equals("SKILL.md"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("SKILL.md not found"));

        String skillMdContent = new String(skillMd.content());
        SkillMetadata metadata = skillMetadataParser.parse(skillMdContent);

        // 5. Run PrePublishValidator
        PrePublishValidator.SkillPackageContext context = new PrePublishValidator.SkillPackageContext(
                entries, metadata, publisherId, namespace.getId());
        ValidationResult prePublishValidation = prePublishValidator.validate(context);
        if (!prePublishValidation.passed()) {
            throw new IllegalArgumentException("Pre-publish validation failed: " + String.join(", ", prePublishValidation.errors()));
        }

        // 6. Find or create Skill record
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), metadata.name())
                .orElseGet(() -> {
                    Skill newSkill = new Skill(namespace.getId(), metadata.name(), publisherId, visibility);
                    newSkill.setCreatedBy(publisherId);
                    return skillRepository.save(newSkill);
                });

        // 7. Check version doesn't already exist
        if (skillVersionRepository.findBySkillIdAndVersion(skill.getId(), metadata.version()).isPresent()) {
            throw new IllegalArgumentException("Version already exists: " + metadata.version());
        }

        // 8. Create SkillVersion
        SkillVersion version = new SkillVersion(skill.getId(), metadata.version(), publisherId);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());

        // Store metadata as JSON
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            version.setParsedMetadataJson(metadataJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize metadata", e);
        }

        version = skillVersionRepository.save(version);

        // 9. Upload each file to storage and compute SHA-256
        List<SkillFile> skillFiles = new ArrayList<>();
        long totalSize = 0;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            HexFormat hexFormat = HexFormat.of();

            for (PackageEntry entry : entries) {
                String storageKey = String.format("skills/%d/%d/%s", skill.getId(), version.getId(), entry.path());

                // Upload to storage
                objectStorageService.putObject(
                        storageKey,
                        new ByteArrayInputStream(entry.content()),
                        entry.size(),
                        entry.contentType()
                );

                // Compute SHA-256
                byte[] hash = digest.digest(entry.content());
                String sha256 = hexFormat.formatHex(hash);

                // Create SkillFile record
                SkillFile skillFile = new SkillFile(
                        version.getId(),
                        entry.path(),
                        entry.size(),
                        entry.contentType(),
                        sha256,
                        storageKey
                );
                skillFiles.add(skillFile);
                totalSize += entry.size();

                digest.reset();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process files", e);
        }

        // 10. Save SkillFile records
        skillFileRepository.saveAll(skillFiles);

        // 11. Update version stats
        version.setFileCount(skillFiles.size());
        version.setTotalSize(totalSize);
        skillVersionRepository.save(version);

        // 12. Update skill
        skill.setLatestVersionId(version.getId());
        skill.setDisplayName(metadata.name());
        skill.setSummary(metadata.description());
        skill.setUpdatedBy(publisherId);
        skillRepository.save(skill);

        // 13. Publish SkillPublishedEvent
        eventPublisher.publishEvent(new SkillPublishedEvent(skill.getId(), version.getId(), publisherId));

        // 14. Return version
        return version;
    }
}
