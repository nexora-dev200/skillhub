package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillDownloadServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillVersionStatsRepository skillVersionStatsRepository;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private SkillTagRepository skillTagRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private VisibilityChecker visibilityChecker;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillDownloadService service;
    private SkillSlugResolutionService skillSlugResolutionService;

    @BeforeEach
    void setUp() {
        skillSlugResolutionService = new SkillSlugResolutionService(skillRepository);
        service = new SkillDownloadService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillVersionStatsRepository,
                skillFileRepository,
                skillTagRepository,
                objectStorageService,
                visibilityChecker,
                eventPublisher,
                skillSlugResolutionService
        );
    }

    @Test
    void testDownloadLatest_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Test Skill");
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        String storageKey = "packages/1/10/bundle.zip";
        InputStream content = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = new ObjectMetadata(1000L, "application/zip", Instant.now());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(objectStorageService.exists(storageKey)).thenReturn(true);
        when(objectStorageService.getMetadata(storageKey)).thenReturn(metadata);
        when(objectStorageService.getObject(storageKey)).thenReturn(content);
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any(), eq("Test Skill-1.0.0.zip"))).thenReturn(null);

        // Act
        SkillDownloadService.DownloadResult result = service.downloadLatest(namespaceSlug, skillSlug, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals("Test Skill-1.0.0.zip", result.filename());
        assertEquals(1000L, result.contentLength());
        assertNotNull(result.openContent());
        verify(skillRepository).incrementDownloadCount(1L);
        verify(skillVersionStatsRepository).incrementDownloadCount(10L, 1L);
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadByTag_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String tagName = "stable";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Test Skill");
        skill.setStatus(SkillStatus.ACTIVE);
        SkillTag tag = new SkillTag(1L, tagName, 10L, userId);
        SkillVersion version = new SkillVersion(1L, "1.0.0", userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        String storageKey = "packages/1/10/bundle.zip";
        InputStream content = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = new ObjectMetadata(1000L, "application/zip", Instant.now());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillTagRepository.findBySkillIdAndTagName(1L, tagName)).thenReturn(Optional.of(tag));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(objectStorageService.exists(storageKey)).thenReturn(true);
        when(objectStorageService.getMetadata(storageKey)).thenReturn(metadata);
        when(objectStorageService.getObject(storageKey)).thenReturn(content);
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any(), eq("Test Skill-1.0.0.zip"))).thenReturn(null);

        // Act
        SkillDownloadService.DownloadResult result = service.downloadByTag(namespaceSlug, skillSlug, tagName, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals("Test Skill-1.0.0.zip", result.filename());
        assertNotNull(result.openContent());
        verify(skillRepository).incrementDownloadCount(1L);
        verify(skillVersionStatsRepository).incrementDownloadCount(10L, 1L);
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_WithPresignedUrlStillProvidesStreamFallback() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String versionStr = "1.0.0";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Generate Commit Message");
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion version = new SkillVersion(1L, versionStr, userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        String storageKey = "packages/1/10/bundle.zip";
        InputStream content = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = new ObjectMetadata(1000L, "application/zip", Instant.now());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, versionStr)).thenReturn(Optional.of(version));
        when(objectStorageService.exists(storageKey)).thenReturn(true);
        when(objectStorageService.getMetadata(storageKey)).thenReturn(metadata);
        when(objectStorageService.getObject(storageKey)).thenReturn(content);
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any(), eq("Generate Commit Message-1.0.0.zip")))
                .thenReturn("http://minio.local/presigned");

        SkillDownloadService.DownloadResult result = service.downloadVersion(namespaceSlug, skillSlug, versionStr, userId, userNsRoles);

        assertEquals("http://minio.local/presigned", result.presignedUrl());
        assertNotNull(result.openContent());
        verify(skillRepository).incrementDownloadCount(1L);
        verify(skillVersionStatsRepository).incrementDownloadCount(10L, 1L);
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_ShouldRejectDraftVersion() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String versionStr = "1.0.0";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion version = new SkillVersion(1L, versionStr, userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.DRAFT);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, versionStr)).thenReturn(Optional.of(version));

        assertThrows(DomainBadRequestException.class, () ->
                service.downloadVersion(namespaceSlug, skillSlug, versionStr, userId, userNsRoles));
        verify(skillRepository, never()).incrementDownloadCount(anyLong());
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_ShouldFallbackToBundledFilesWhenBundleIsMissing() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String versionStr = "1.0.0";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Generate Commit Message");
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion version = new SkillVersion(1L, versionStr, userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile file = new SkillFile(10L, "SKILL.md", 4L, "text/markdown", "hash", "skills/1/10/SKILL.md");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, versionStr)).thenReturn(Optional.of(version));
        when(objectStorageService.exists("packages/1/10/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(10L)).thenReturn(List.of(file));
        when(objectStorageService.exists("skills/1/10/SKILL.md")).thenReturn(true);
        when(objectStorageService.getObject("skills/1/10/SKILL.md")).thenReturn(new ByteArrayInputStream("test".getBytes()));

        SkillDownloadService.DownloadResult result = service.downloadVersion(namespaceSlug, skillSlug, versionStr, userId, userNsRoles);

        assertNull(result.presignedUrl());
        assertTrue(result.fallbackBundle());
        assertEquals("Generate Commit Message-1.0.0.zip", result.filename());
        assertEquals("application/zip", result.contentType());
        assertTrue(result.contentLength() > 0);

        try (ZipInputStream zipInputStream = new ZipInputStream(result.openContent())) {
            var entry = zipInputStream.getNextEntry();
            assertNotNull(entry);
            assertEquals("SKILL.md", entry.getName());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            zipInputStream.transferTo(output);
            assertEquals("test", output.toString());
        }

        verify(skillRepository).incrementDownloadCount(1L);
        verify(skillVersionStatsRepository).incrementDownloadCount(10L, 1L);
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_AllowsAnonymousForGlobalPublicSkill() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "system");
        setId(namespace, 1L);
        namespace.setType(NamespaceType.GLOBAL);

        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Demo Skill");
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo-skill")).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, null, Map.of())).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, "1.0.0")).thenReturn(Optional.of(version));
        when(objectStorageService.exists("packages/1/10/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(10L)).thenReturn(List.of(
                new SkillFile(10L, "SKILL.md", 4L, "text/markdown", "hash", "skills/1/10/SKILL.md")));
        when(objectStorageService.exists("skills/1/10/SKILL.md")).thenReturn(true);
        when(objectStorageService.getObject("skills/1/10/SKILL.md")).thenReturn(new ByteArrayInputStream("test".getBytes()));

        SkillDownloadService.DownloadResult result = service.downloadVersion("global", "demo-skill", "1.0.0", null, Map.of());

        assertNotNull(result);
        assertEquals("Demo Skill-1.0.0.zip", result.filename());
        verify(skillRepository).incrementDownloadCount(1L);
        verify(skillVersionStatsRepository).incrementDownloadCount(10L, 1L);
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_RejectsAnonymousWhenVisibilityCheckerDeniesAccess() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        Skill skill = new Skill(2L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(2L, "demo-skill")).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, null, Map.of())).thenReturn(false);

        assertThrows(DomainForbiddenException.class, () ->
                service.downloadVersion("team-ai", "demo-skill", "1.0.0", null, Map.of()));

        verify(visibilityChecker).canAccess(skill, null, Map.of());
        verify(skillRepository, never()).incrementDownloadCount(anyLong());
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadNamespaceBundle_PackagesVisiblePublishedSkills() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        Skill alpha = new Skill(2L, "alpha", "owner-1", SkillVisibility.PUBLIC);
        setId(alpha, 11L);
        alpha.setDisplayName("Alpha Skill");
        alpha.setStatus(SkillStatus.ACTIVE);
        alpha.setLatestVersionId(101L);

        Skill beta = new Skill(2L, "beta", "owner-1", SkillVisibility.PUBLIC);
        setId(beta, 12L);
        beta.setDisplayName("Beta Skill");
        beta.setStatus(SkillStatus.ACTIVE);
        beta.setLatestVersionId(102L);

        SkillVersion alphaVersion = new SkillVersion(11L, "1.0.0", "owner-1");
        setId(alphaVersion, 101L);
        alphaVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion betaVersion = new SkillVersion(12L, "2.0.0", "owner-1");
        setId(betaVersion, 102L);
        betaVersion.setStatus(SkillVersionStatus.PUBLISHED);

        SkillFile alphaFile = new SkillFile(101L, "SKILL.md", 5L, "text/markdown", "hash-a", "skills/11/101/SKILL.md");
        SkillFile betaFile = new SkillFile(102L, "README.md", 4L, "text/markdown", "hash-b", "skills/12/102/README.md");

        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(2L, SkillStatus.ACTIVE)).thenReturn(List.of(alpha, beta));
        when(visibilityChecker.canAccess(alpha, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
        when(visibilityChecker.canAccess(beta, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(alphaVersion));
        when(skillVersionRepository.findById(102L)).thenReturn(Optional.of(betaVersion));
        when(objectStorageService.exists("packages/11/101/bundle.zip")).thenReturn(false);
        when(objectStorageService.exists("packages/12/102/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(101L)).thenReturn(List.of(alphaFile));
        when(skillFileRepository.findByVersionId(102L)).thenReturn(List.of(betaFile));
        when(objectStorageService.exists("skills/11/101/SKILL.md")).thenReturn(true);
        when(objectStorageService.exists("skills/12/102/README.md")).thenReturn(true);
        when(objectStorageService.getObject("skills/11/101/SKILL.md")).thenReturn(new ByteArrayInputStream("alpha".getBytes()));
        when(objectStorageService.getObject("skills/12/102/README.md")).thenReturn(new ByteArrayInputStream("beta".getBytes()));

        SkillDownloadService.DownloadResult result = service.downloadNamespaceBundle(
                "team-ai",
                List.of(),
                "user-1",
                Map.of(2L, NamespaceRole.MEMBER));

        assertEquals("team-ai-skills.zip", result.filename());
        assertEquals("application/zip", result.contentType());
        assertNull(result.presignedUrl());
        assertTrue(result.contentLength() > 0);

        try (ZipInputStream zipInputStream = new ZipInputStream(result.openContent())) {
            var firstEntry = zipInputStream.getNextEntry();
            assertNotNull(firstEntry);
            assertEquals("team-ai/alpha-1.0.0.zip", firstEntry.getName());
            var secondEntry = zipInputStream.getNextEntry();
            assertNotNull(secondEntry);
            assertEquals("team-ai/beta-2.0.0.zip", secondEntry.getName());
        }

        verify(skillRepository).incrementDownloadCount(11L);
        verify(skillRepository).incrementDownloadCount(12L);
        verify(skillVersionStatsRepository).incrementDownloadCount(101L, 11L);
        verify(skillVersionStatsRepository).incrementDownloadCount(102L, 12L);
        verify(eventPublisher, times(2)).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadNamespaceBundle_SkipsInvisibleAndUnpublishedSkills() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        Skill visible = new Skill(2L, "alpha", "owner-1", SkillVisibility.PUBLIC);
        setId(visible, 11L);
        visible.setDisplayName("Alpha Skill");
        visible.setStatus(SkillStatus.ACTIVE);
        visible.setLatestVersionId(101L);

        Skill invisible = new Skill(2L, "beta-private", "owner-2", SkillVisibility.PRIVATE);
        setId(invisible, 12L);
        invisible.setDisplayName("Beta Private");
        invisible.setStatus(SkillStatus.ACTIVE);
        invisible.setLatestVersionId(102L);

        Skill draftOnly = new Skill(2L, "gamma-draft", "owner-1", SkillVisibility.PUBLIC);
        setId(draftOnly, 13L);
        draftOnly.setDisplayName("Gamma Draft");
        draftOnly.setStatus(SkillStatus.ACTIVE);
        draftOnly.setLatestVersionId(103L);

        SkillVersion visibleVersion = new SkillVersion(11L, "1.0.0", "owner-1");
        setId(visibleVersion, 101L);
        visibleVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion privateVersion = new SkillVersion(12L, "1.0.0", "owner-2");
        setId(privateVersion, 102L);
        privateVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion draftVersion = new SkillVersion(13L, "0.1.0", "owner-1");
        setId(draftVersion, 103L);
        draftVersion.setStatus(SkillVersionStatus.DRAFT);

        SkillFile alphaFile = new SkillFile(101L, "SKILL.md", 5L, "text/markdown", "hash-a", "skills/11/101/SKILL.md");

        Map<Long, NamespaceRole> roles = Map.of(2L, NamespaceRole.MEMBER);
        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(2L, SkillStatus.ACTIVE)).thenReturn(List.of(visible, invisible, draftOnly));
        when(visibilityChecker.canAccess(visible, "user-1", roles)).thenReturn(true);
        when(visibilityChecker.canAccess(invisible, "user-1", roles)).thenReturn(false);
        when(visibilityChecker.canAccess(draftOnly, "user-1", roles)).thenReturn(true);
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(visibleVersion));
        when(skillVersionRepository.findById(103L)).thenReturn(Optional.of(draftVersion));
        when(objectStorageService.exists("packages/11/101/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(101L)).thenReturn(List.of(alphaFile));
        when(objectStorageService.exists("skills/11/101/SKILL.md")).thenReturn(true);
        when(objectStorageService.getObject("skills/11/101/SKILL.md")).thenReturn(new ByteArrayInputStream("alpha".getBytes()));

        SkillDownloadService.DownloadResult result = service.downloadNamespaceBundle(
                "team-ai",
                List.of(),
                "user-1",
                roles);

        try (ZipInputStream zipInputStream = new ZipInputStream(result.openContent())) {
            var firstEntry = zipInputStream.getNextEntry();
            assertNotNull(firstEntry);
            assertEquals("team-ai/alpha-1.0.0.zip", firstEntry.getName());
            assertNull(zipInputStream.getNextEntry());
        }

        verify(skillVersionRepository, never()).findById(102L);
        verify(skillRepository).incrementDownloadCount(11L);
        verify(skillRepository, never()).incrementDownloadCount(12L);
        verify(skillRepository, never()).incrementDownloadCount(13L);
        verify(skillVersionStatsRepository).incrementDownloadCount(101L, 11L);
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(102L, 12L);
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(103L, 13L);
        verify(eventPublisher, times(1)).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadNamespaceBundle_RejectsAnonymousAllSkillsRequest() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setId(namespace, 1L);
        namespace.setType(NamespaceType.GLOBAL);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.downloadNamespaceBundle("global", List.of(), null, Map.of()));

        assertEquals("error.namespace.skills.download.selectionRequired", ex.getMessage());
        verifyNoInteractions(objectStorageService);
        verify(skillRepository, never()).incrementDownloadCount(anyLong());
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadNamespaceBundle_RejectsTooManyEligibleSkills() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        List<Skill> skills = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            Skill skill = new Skill(2L, "skill-" + i, "owner-1", SkillVisibility.PUBLIC);
            setId(skill, (long) i);
            skill.setStatus(SkillStatus.ACTIVE);
            skill.setLatestVersionId(100L + i);
            skills.add(skill);

            SkillVersion version = new SkillVersion((long) i, "1.0.0", "owner-1");
            setId(version, 100L + i);
            version.setStatus(SkillVersionStatus.PUBLISHED);
            when(visibilityChecker.canAccess(skill, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
            when(skillVersionRepository.findById(100L + i)).thenReturn(Optional.of(version));
        }

        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(2L, SkillStatus.ACTIVE)).thenReturn(skills);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.downloadNamespaceBundle("team-ai", List.of(), "user-1", Map.of(2L, NamespaceRole.MEMBER)));

        assertEquals("error.namespace.skills.download.tooMany", ex.getMessage());
        verifyNoInteractions(objectStorageService);
        verify(skillRepository, never()).incrementDownloadCount(anyLong());
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadNamespaceBundle_RejectsOversizedAggregateBundle() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        Skill alpha = new Skill(2L, "alpha", "owner-1", SkillVisibility.PUBLIC);
        setId(alpha, 11L);
        alpha.setDisplayName("Alpha Skill");
        alpha.setStatus(SkillStatus.ACTIVE);
        alpha.setLatestVersionId(101L);

        Skill beta = new Skill(2L, "beta", "owner-1", SkillVisibility.PUBLIC);
        setId(beta, 12L);
        beta.setDisplayName("Beta Skill");
        beta.setStatus(SkillStatus.ACTIVE);
        beta.setLatestVersionId(102L);

        SkillVersion alphaVersion = new SkillVersion(11L, "1.0.0", "owner-1");
        setId(alphaVersion, 101L);
        alphaVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion betaVersion = new SkillVersion(12L, "1.0.0", "owner-1");
        setId(betaVersion, 102L);
        betaVersion.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(2L, SkillStatus.ACTIVE)).thenReturn(List.of(alpha, beta));
        when(visibilityChecker.canAccess(alpha, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
        when(visibilityChecker.canAccess(beta, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(alphaVersion));
        when(skillVersionRepository.findById(102L)).thenReturn(Optional.of(betaVersion));
        when(objectStorageService.exists("packages/11/101/bundle.zip")).thenReturn(true);
        when(objectStorageService.exists("packages/12/102/bundle.zip")).thenReturn(true);
        when(objectStorageService.getMetadata("packages/11/101/bundle.zip"))
                .thenReturn(new ObjectMetadata(60L * 1024 * 1024, "application/zip", Instant.now()));
        when(objectStorageService.getMetadata("packages/12/102/bundle.zip"))
                .thenReturn(new ObjectMetadata(60L * 1024 * 1024, "application/zip", Instant.now()));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.downloadNamespaceBundle("team-ai", List.of(), "user-1", Map.of(2L, NamespaceRole.MEMBER)));

        assertEquals("error.namespace.skills.download.tooLarge", ex.getMessage());
        verify(objectStorageService, never()).getObject(anyString());
        verify(skillRepository, never()).incrementDownloadCount(anyLong());
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadNamespaceBundle_RejectsOversizedFallbackBundleBeforeReadingFiles() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        Skill alpha = new Skill(2L, "alpha", "owner-1", SkillVisibility.PUBLIC);
        setId(alpha, 11L);
        alpha.setDisplayName("Alpha Skill");
        alpha.setStatus(SkillStatus.ACTIVE);
        alpha.setLatestVersionId(101L);

        Skill beta = new Skill(2L, "beta", "owner-1", SkillVisibility.PUBLIC);
        setId(beta, 12L);
        beta.setDisplayName("Beta Skill");
        beta.setStatus(SkillStatus.ACTIVE);
        beta.setLatestVersionId(102L);

        SkillVersion alphaVersion = new SkillVersion(11L, "1.0.0", "owner-1");
        setId(alphaVersion, 101L);
        alphaVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion betaVersion = new SkillVersion(12L, "1.0.0", "owner-1");
        setId(betaVersion, 102L);
        betaVersion.setStatus(SkillVersionStatus.PUBLISHED);

        SkillFile alphaFile = new SkillFile(101L, "SKILL.md", 60L * 1024 * 1024, "text/markdown", "hash-a", "skills/11/101/SKILL.md");
        SkillFile betaFile = new SkillFile(102L, "README.md", 60L * 1024 * 1024, "text/markdown", "hash-b", "skills/12/102/README.md");

        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(2L, SkillStatus.ACTIVE)).thenReturn(List.of(alpha, beta));
        when(visibilityChecker.canAccess(alpha, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
        when(visibilityChecker.canAccess(beta, "user-1", Map.of(2L, NamespaceRole.MEMBER))).thenReturn(true);
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(alphaVersion));
        when(skillVersionRepository.findById(102L)).thenReturn(Optional.of(betaVersion));
        when(objectStorageService.exists("packages/11/101/bundle.zip")).thenReturn(false);
        when(objectStorageService.exists("packages/12/102/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(101L)).thenReturn(List.of(alphaFile));
        when(skillFileRepository.findByVersionId(102L)).thenReturn(List.of(betaFile));
        when(objectStorageService.exists("skills/11/101/SKILL.md")).thenReturn(true);
        when(objectStorageService.exists("skills/12/102/README.md")).thenReturn(true);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.downloadNamespaceBundle("team-ai", List.of(), "user-1", Map.of(2L, NamespaceRole.MEMBER)));

        assertEquals("error.namespace.skills.download.tooLarge", ex.getMessage());
        verify(objectStorageService, never()).getObject(anyString());
        verify(skillRepository, never()).incrementDownloadCount(anyLong());
        verify(skillVersionStatsRepository, never()).incrementDownloadCount(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(SkillDownloadedEvent.class));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
