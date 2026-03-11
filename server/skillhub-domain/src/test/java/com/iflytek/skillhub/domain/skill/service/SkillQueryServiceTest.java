package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillQueryServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private VisibilityChecker visibilityChecker;

    private SkillQueryService service;

    @BeforeEach
    void setUp() {
        service = new SkillQueryService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                objectStorageService,
                visibilityChecker
        );
    }

    @Test
    void testGetSkillDetail_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        Long userId = 100L;
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Test Skill");
        skill.setSummary("Test Summary");
        skill.setLatestVersionId(10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", userId);
        setId(version, 10L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));

        // Act
        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals(skillSlug, result.slug());
        assertEquals("Test Skill", result.displayName());
        assertEquals("1.0.0", result.latestVersion());
    }

    @Test
    void testGetSkillDetail_AccessDenied() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        Long userId = 100L;
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, 200L, SkillVisibility.PRIVATE);
        setId(skill, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(false);

        // Act & Assert
        assertThrows(SecurityException.class, () ->
                service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles)
        );
    }

    @Test
    void testListSkillsByNamespace() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        Long userId = 100L;
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);
        Pageable pageable = PageRequest.of(0, 10);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill1 = new Skill(1L, "skill1", userId, SkillVisibility.PUBLIC);
        setId(skill1, 1L);
        Skill skill2 = new Skill(1L, "skill2", userId, SkillVisibility.PRIVATE);
        setId(skill2, 2L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE)).thenReturn(List.of(skill1, skill2));
        when(visibilityChecker.canAccess(skill1, userId, userNsRoles)).thenReturn(true);
        when(visibilityChecker.canAccess(skill2, userId, userNsRoles)).thenReturn(false);

        // Act
        Page<Skill> result = service.listSkillsByNamespace(namespaceSlug, userId, userNsRoles, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("skill1", result.getContent().get(0).getSlug());
    }

    @Test
    void testListFiles() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.0.0";

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, 100L, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        SkillVersion skillVersion = new SkillVersion(1L, version, 100L);
        setId(skillVersion, 1L);
        SkillFile file1 = new SkillFile(1L, "file1.txt", 100L, "text/plain", "hash1", "key1");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));
        when(skillFileRepository.findByVersionId(1L)).thenReturn(List.of(file1));

        // Act
        List<SkillFile> result = service.listFiles(namespaceSlug, skillSlug, version);

        // Assert
        assertEquals(1, result.size());
        assertEquals("file1.txt", result.get(0).getFilePath());
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
