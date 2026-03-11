package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillTag;
import com.iflytek.skillhub.domain.skill.SkillTagRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillTagJpaRepository extends JpaRepository<SkillTag, Long>, SkillTagRepository {
    Optional<SkillTag> findBySkillIdAndTagName(Long skillId, String tagName);
    List<SkillTag> findBySkillId(Long skillId);
}
