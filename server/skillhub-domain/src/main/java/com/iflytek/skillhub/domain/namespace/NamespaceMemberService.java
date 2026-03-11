package com.iflytek.skillhub.domain.namespace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class NamespaceMemberService {

    private final NamespaceMemberRepository namespaceMemberRepository;

    public NamespaceMemberService(NamespaceMemberRepository namespaceMemberRepository) {
        this.namespaceMemberRepository = namespaceMemberRepository;
    }

    @Transactional
    public NamespaceMember addMember(Long namespaceId, Long userId, NamespaceRole role) {
        if (role == NamespaceRole.OWNER) {
            throw new IllegalArgumentException("Cannot directly assign OWNER role");
        }

        if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId).isPresent()) {
            throw new IllegalArgumentException("User is already a member of this namespace");
        }

        NamespaceMember member = new NamespaceMember(namespaceId, userId, role);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long namespaceId, Long userId) {
        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (member.getRole() == NamespaceRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove OWNER from namespace");
        }

        namespaceMemberRepository.deleteByNamespaceIdAndUserId(namespaceId, userId);
    }

    @Transactional
    public NamespaceMember updateMemberRole(Long namespaceId, Long userId, NamespaceRole newRole) {
        if (newRole == NamespaceRole.OWNER) {
            throw new IllegalArgumentException("Cannot set OWNER role directly. Use transferOwnership instead");
        }

        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        member.setRole(newRole);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void transferOwnership(Long namespaceId, Long currentOwnerId, Long newOwnerId) {
        NamespaceMember currentOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("Current owner not found"));

        if (currentOwner.getRole() != NamespaceRole.OWNER) {
            throw new IllegalArgumentException("User is not the current owner");
        }

        NamespaceMember newOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("New owner not found in namespace"));

        currentOwner.setRole(NamespaceRole.ADMIN);
        newOwner.setRole(NamespaceRole.OWNER);

        namespaceMemberRepository.save(currentOwner);
        namespaceMemberRepository.save(newOwner);
    }

    public Optional<NamespaceRole> getMemberRole(Long namespaceId, Long userId) {
        return namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .map(NamespaceMember::getRole);
    }

    public Page<NamespaceMember> listMembers(Long namespaceId, Pageable pageable) {
        return namespaceMemberRepository.findByNamespaceId(namespaceId, pageable);
    }
}
