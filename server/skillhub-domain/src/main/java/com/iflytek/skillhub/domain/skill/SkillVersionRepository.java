package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillVersionRepository {
    Optional<SkillVersion> findById(Long id);
    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);
    List<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status);
    SkillVersion save(SkillVersion version);
}
