package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA adapter for Suite version snapshots. */
@Repository
public interface SkillSuiteVersionJpaRepository
        extends JpaRepository<SkillSuiteVersion, Long>, SkillSuiteVersionRepository {
}
