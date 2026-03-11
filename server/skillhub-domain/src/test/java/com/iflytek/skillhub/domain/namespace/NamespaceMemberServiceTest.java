package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceMemberServiceTest {

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @InjectMocks
    private NamespaceMemberService namespaceMemberService;

    @Test
    void addMember_shouldAddMemberSuccessfully() {
        Long namespaceId = 1L;
        Long userId = 2L;
        NamespaceRole role = NamespaceRole.MEMBER;

        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class)))
                .thenReturn(new NamespaceMember(namespaceId, userId, role));

        NamespaceMember result = namespaceMemberService.addMember(namespaceId, userId, role);

        assertNotNull(result);
        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void addMember_shouldThrowExceptionForOwnerRole() {
        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.addMember(1L, 2L, NamespaceRole.OWNER));
    }

    @Test
    void addMember_shouldThrowExceptionWhenMemberExists() {
        Long namespaceId = 1L;
        Long userId = 2L;
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(new NamespaceMember()));

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.addMember(namespaceId, userId, NamespaceRole.MEMBER));
    }

    @Test
    void removeMember_shouldThrowExceptionForOwner() {
        Long namespaceId = 1L;
        Long userId = 2L;
        NamespaceMember ownerMember = new NamespaceMember(namespaceId, userId, NamespaceRole.OWNER);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(ownerMember));

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.removeMember(namespaceId, userId));
    }

    @Test
    void removeMember_shouldThrowExceptionWhenMemberNotFound() {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.removeMember(1L, 2L));
    }

    @Test
    void updateMemberRole_shouldUpdateRoleSuccessfully() {
        Long namespaceId = 1L;
        Long userId = 2L;
        NamespaceMember member = new NamespaceMember(namespaceId, userId, NamespaceRole.MEMBER);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(member));
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenReturn(member);

        NamespaceMember result = namespaceMemberService.updateMemberRole(namespaceId, userId, NamespaceRole.ADMIN);

        assertNotNull(result);
        verify(namespaceMemberRepository).save(member);
    }

    @Test
    void updateMemberRole_shouldThrowExceptionForOwnerRole() {
        Long namespaceId = 1L;
        Long userId = 2L;

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.updateMemberRole(namespaceId, userId, NamespaceRole.OWNER));
    }

    @Test
    void updateMemberRole_shouldThrowExceptionWhenMemberNotFound() {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.updateMemberRole(1L, 2L, NamespaceRole.ADMIN));
    }

    @Test
    void transferOwnership_shouldTransferOwnershipSuccessfully() {
        Long namespaceId = 1L;
        Long currentOwnerId = 2L;
        Long newOwnerId = 3L;

        NamespaceMember currentOwner = new NamespaceMember(namespaceId, currentOwnerId, NamespaceRole.OWNER);
        NamespaceMember newOwner = new NamespaceMember(namespaceId, newOwnerId, NamespaceRole.ADMIN);

        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId))
                .thenReturn(Optional.of(currentOwner));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId))
                .thenReturn(Optional.of(newOwner));
        when(namespaceMemberRepository.save(any(NamespaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        namespaceMemberService.transferOwnership(namespaceId, currentOwnerId, newOwnerId);

        verify(namespaceMemberRepository, times(2)).save(any(NamespaceMember.class));
    }

    @Test
    void transferOwnership_shouldThrowExceptionWhenCurrentOwnerNotFound() {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.transferOwnership(1L, 2L, 3L));
    }

    @Test
    void transferOwnership_shouldThrowExceptionWhenCurrentUserIsNotOwner() {
        Long namespaceId = 1L;
        Long currentOwnerId = 2L;
        NamespaceMember notOwner = new NamespaceMember(namespaceId, currentOwnerId, NamespaceRole.ADMIN);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId))
                .thenReturn(Optional.of(notOwner));

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.transferOwnership(namespaceId, currentOwnerId, 3L));
    }

    @Test
    void transferOwnership_shouldThrowExceptionWhenNewOwnerNotFound() {
        Long namespaceId = 1L;
        Long currentOwnerId = 2L;
        Long newOwnerId = 3L;
        NamespaceMember currentOwner = new NamespaceMember(namespaceId, currentOwnerId, NamespaceRole.OWNER);

        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId))
                .thenReturn(Optional.of(currentOwner));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                namespaceMemberService.transferOwnership(namespaceId, currentOwnerId, newOwnerId));
    }

    @Test
    void getMemberRole_shouldReturnRole() {
        Long namespaceId = 1L;
        Long userId = 2L;
        NamespaceMember member = new NamespaceMember(namespaceId, userId, NamespaceRole.ADMIN);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(member));

        Optional<NamespaceRole> result = namespaceMemberService.getMemberRole(namespaceId, userId);

        assertTrue(result.isPresent());
        assertEquals(NamespaceRole.ADMIN, result.get());
    }

    @Test
    void getMemberRole_shouldReturnEmptyWhenMemberNotFound() {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());

        Optional<NamespaceRole> result = namespaceMemberService.getMemberRole(1L, 2L);

        assertFalse(result.isPresent());
    }
}
