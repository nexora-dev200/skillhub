package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillTagServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillTagRepository skillTagRepository;

    private SkillTagService service;

    @BeforeEach
    void setUp() {
        service = new SkillTagService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillTagRepository
        );
    }

    @Test
    void testCreateTag_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String tagName = "stable";
        String targetVersion = "1.0.0";
        Long operatorId = 100L;

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, operatorId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        SkillVersion version = new SkillVersion(1L, targetVersion, operatorId);
        setId(version, 1L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        SkillTag tag = new SkillTag(1L, tagName, 1L, operatorId);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, targetVersion)).thenReturn(Optional.of(version));
        when(skillTagRepository.findBySkillIdAndTagName(1L, tagName)).thenReturn(Optional.empty());
        when(skillTagRepository.save(any())).thenReturn(tag);

        // Act
        SkillTag result = service.createOrMoveTag(namespaceSlug, skillSlug, tagName, targetVersion, operatorId);

        // Assert
        assertNotNull(result);
        verify(skillTagRepository).save(any(SkillTag.class));
    }

    @Test
    void testCreateTag_RejectLatest() {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String tagName = "latest";
        String targetVersion = "1.0.0";
        Long operatorId = 100L;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                service.createOrMoveTag(namespaceSlug, skillSlug, tagName, targetVersion, operatorId)
        );
    }

    @Test
    void testDeleteTag_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String tagName = "stable";
        Long operatorId = 100L;

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, operatorId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        SkillTag tag = new SkillTag(1L, tagName, 1L, operatorId);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(skillTagRepository.findBySkillIdAndTagName(1L, tagName)).thenReturn(Optional.of(tag));

        // Act
        service.deleteTag(namespaceSlug, skillSlug, tagName, operatorId);

        // Assert
        verify(skillTagRepository).delete(tag);
    }

    @Test
    void testDeleteTag_RejectLatest() {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String tagName = "latest";
        Long operatorId = 100L;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                service.deleteTag(namespaceSlug, skillSlug, tagName, operatorId)
        );
    }

    @Test
    void testListTags() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", 1L);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, 100L, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        SkillTag tag1 = new SkillTag(1L, "stable", 1L, 100L);
        SkillTag tag2 = new SkillTag(1L, "beta", 2L, 100L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(skillTagRepository.findBySkillId(1L)).thenReturn(List.of(tag1, tag2));

        // Act
        List<SkillTag> result = service.listTags(namespaceSlug, skillSlug);

        // Assert
        assertEquals(2, result.size());
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
