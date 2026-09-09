package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SkillSuiteVersionOptimisticLockingTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publishingRejectsAConcurrentStaleDraftUpdate() {
        PersistedSuite persisted = persistDraft();
        EntityManager publisher = entityManagerFactory.createEntityManager();
        EntityManager staleEditor = entityManagerFactory.createEntityManager();
        try {
            publisher.getTransaction().begin();
            staleEditor.getTransaction().begin();
            SkillSuiteVersion published = publisher.find(SkillSuiteVersion.class, persisted.versionId());
            SkillSuiteVersion stale = staleEditor.find(SkillSuiteVersion.class, persisted.versionId());

            published.setStatus(SkillSuiteVersionStatus.PUBLISHED);
            published.setPublishedAt(Instant.parse("2026-09-09T08:00:00Z"));
            publisher.getTransaction().commit();

            stale.setDisplayName("Edited after publish");
            assertThatThrownBy(staleEditor.getTransaction()::commit)
                    .satisfies(error -> assertThat(hasCause(error, OptimisticLockException.class)).isTrue());

            EntityManager verifier = entityManagerFactory.createEntityManager();
            try {
                SkillSuiteVersion saved = verifier.find(SkillSuiteVersion.class, persisted.versionId());
                assertThat(saved.getStatus()).isEqualTo(SkillSuiteVersionStatus.PUBLISHED);
                assertThat(saved.getDisplayName()).isEqualTo("Original draft");
                assertThat(saved.getPublishedAt()).isNotNull();
            } finally {
                verifier.close();
            }
        } finally {
            rollbackIfActive(publisher);
            rollbackIfActive(staleEditor);
            publisher.close();
            staleEditor.close();
            deleteSuite(persisted);
        }
    }

    private PersistedSuite persistDraft() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            Namespace namespace = new Namespace("suite-locking", "Suite locking", "owner");
            entityManager.persist(namespace);
            SkillSuite suite = new SkillSuite(namespace.getId(), "starter", "Starter", "owner");
            entityManager.persist(suite);
            SkillSuiteVersion version = new SkillSuiteVersion(
                    suite.getId(), "1.0.0", "Original draft", "Summary", SkillVisibility.PUBLIC, "owner");
            entityManager.persist(version);
            entityManager.getTransaction().commit();
            return new PersistedSuite(namespace.getId(), suite.getId(), version.getId());
        } finally {
            rollbackIfActive(entityManager);
            entityManager.close();
        }
    }

    private void deleteSuite(PersistedSuite persisted) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.createQuery("DELETE FROM SkillSuiteVersion version WHERE version.id = :id")
                    .setParameter("id", persisted.versionId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM SkillSuite suite WHERE suite.id = :id")
                    .setParameter("id", persisted.suiteId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Namespace namespace WHERE namespace.id = :id")
                    .setParameter("id", persisted.namespaceId())
                    .executeUpdate();
            entityManager.getTransaction().commit();
        } finally {
            rollbackIfActive(entityManager);
            entityManager.close();
        }
    }

    private void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> expectedType) {
        Throwable current = error;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record PersistedSuite(Long namespaceId, Long suiteId, Long versionId) {
    }
}
