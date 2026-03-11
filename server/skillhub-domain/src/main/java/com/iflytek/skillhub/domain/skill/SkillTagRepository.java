package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

public interface SkillTagRepository {
    Optional<SkillTag> findBySkillIdAndTagName(Long skillId, String tagName);
    List<SkillTag> findBySkillId(Long skillId);
    SkillTag save(SkillTag tag);
    void delete(SkillTag tag);
}
