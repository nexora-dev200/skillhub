package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkillTagService {

    private static final String RESERVED_TAG_LATEST = "latest";

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillTagRepository skillTagRepository;

    public SkillTagService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillTagRepository skillTagRepository) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillTagRepository = skillTagRepository;
    }

    public List<SkillTag> listTags(String namespaceSlug, String skillSlug) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        return skillTagRepository.findBySkillId(skill.getId());
    }

    @Transactional
    public SkillTag createOrMoveTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String targetVersion,
            Long operatorId) {

        // Reject "latest" tag
        if (RESERVED_TAG_LATEST.equalsIgnoreCase(tagName)) {
            throw new IllegalArgumentException("Tag name 'latest' is reserved");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        // Find target version
        SkillVersion version = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + targetVersion));

        // Target must be PUBLISHED
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("Target version must be PUBLISHED");
        }

        // Check if tag exists
        SkillTag existingTag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName).orElse(null);

        if (existingTag != null) {
            // Move tag
            existingTag.setVersionId(version.getId());
            return skillTagRepository.save(existingTag);
        } else {
            // Create new tag
            SkillTag newTag = new SkillTag(skill.getId(), tagName, version.getId(), operatorId);
            return skillTagRepository.save(newTag);
        }
    }

    @Transactional
    public void deleteTag(String namespaceSlug, String skillSlug, String tagName, Long operatorId) {
        // Reject "latest" tag
        if (RESERVED_TAG_LATEST.equalsIgnoreCase(tagName)) {
            throw new IllegalArgumentException("Tag name 'latest' is reserved and cannot be deleted");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillSlug));

        SkillTag tag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagName));

        skillTagRepository.delete(tag);
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + slug));
    }
}
