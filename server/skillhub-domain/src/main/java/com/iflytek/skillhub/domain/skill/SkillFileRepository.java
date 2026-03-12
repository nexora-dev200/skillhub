package com.iflytek.skillhub.domain.skill;

import java.util.List;

public interface SkillFileRepository {
    List<SkillFile> findByVersionId(Long versionId);
    SkillFile save(SkillFile file);
    <S extends SkillFile> List<S> saveAll(Iterable<S> files);
    void deleteByVersionId(Long versionId);
}
