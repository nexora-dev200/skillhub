package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @InjectMocks
    private NamespaceService namespaceService;

    @Test
    void createNamespace_shouldCreateNamespaceAndOwnerMember() {
        String slug = "test-namespace";
        String displayName = "Test Namespace";
        String description = "Test description";
        Long creatorUserId = 1L;

        Namespace savedNamespace = new Namespace(slug, displayName, creatorUserId);
        when(namespaceRepository.findBySlug(slug)).thenReturn(Optional.empty());
        when(namespaceRepository.save(any(Namespace.class))).thenReturn(savedNamespace);
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenReturn(new NamespaceMember());

        Namespace result = namespaceService.createNamespace(slug, displayName, description, creatorUserId);

        assertNotNull(result);
        assertEquals(slug, result.getSlug());
        assertEquals(displayName, result.getDisplayName());
        verify(namespaceRepository).save(any(Namespace.class));
        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void createNamespace_shouldThrowExceptionWhenSlugExists() {
        String slug = "existing-slug";
        when(namespaceRepository.findBySlug(slug)).thenReturn(Optional.of(new Namespace()));

        assertThrows(IllegalArgumentException.class, () ->
                namespaceService.createNamespace(slug, "Name", "Desc", 1L));
    }

    @Test
    void createNamespace_shouldThrowExceptionForInvalidSlug() {
        assertThrows(IllegalArgumentException.class, () ->
                namespaceService.createNamespace("INVALID", "Name", "Desc", 1L));
    }

    @Test
    void updateNamespace_shouldUpdateFields() {
        Long namespaceId = 1L;
        Namespace namespace = new Namespace("slug", "Old Name", 1L);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceRepository.save(any(Namespace.class))).thenReturn(namespace);

        Namespace result = namespaceService.updateNamespace(namespaceId, "New Name", "New Desc", "http://avatar.url");

        assertNotNull(result);
        verify(namespaceRepository).save(namespace);
    }

    @Test
    void updateNamespace_shouldThrowExceptionWhenNotFound() {
        when(namespaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                namespaceService.updateNamespace(1L, "Name", "Desc", null));
    }

    @Test
    void getNamespaceBySlug_shouldReturnNamespace() {
        String slug = "test-slug";
        Namespace namespace = new Namespace(slug, "Name", 1L);
        when(namespaceRepository.findBySlug(slug)).thenReturn(Optional.of(namespace));

        Namespace result = namespaceService.getNamespaceBySlug(slug);

        assertNotNull(result);
        assertEquals(slug, result.getSlug());
    }

    @Test
    void getNamespaceBySlug_shouldThrowExceptionWhenNotFound() {
        when(namespaceRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                namespaceService.getNamespaceBySlug("nonexistent"));
    }
}
