package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillRepository {
    Optional<Skill> findById(Long id);
    Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status);
    Skill save(Skill skill);
    List<Skill> findByOwnerId(Long ownerId);
    void incrementDownloadCount(Long skillId);
}
