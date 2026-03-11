package com.iflytek.skillhub.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillSearchDocumentJpaRepository extends JpaRepository<SkillSearchDocumentEntity, Long> {
    Optional<SkillSearchDocumentEntity> findBySkillId(Long skillId);
    void deleteBySkillId(Long skillId);
}
