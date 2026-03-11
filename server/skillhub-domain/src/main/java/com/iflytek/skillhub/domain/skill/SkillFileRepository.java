package com.iflytek.skillhub.domain.skill;

import java.util.List;

public interface SkillFileRepository {
    List<SkillFile> findByVersionId(Long versionId);
    SkillFile save(SkillFile file);
    void saveAll(List<SkillFile> files);
    void deleteByVersionId(Long versionId);
}
