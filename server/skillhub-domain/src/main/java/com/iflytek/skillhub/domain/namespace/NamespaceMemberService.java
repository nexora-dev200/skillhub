package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class NamespaceMemberService {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final NamespaceService namespaceService;

    public NamespaceMemberService(NamespaceMemberRepository namespaceMemberRepository,
                                  NamespaceService namespaceService) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceService = namespaceService;
    }

    @Transactional
    public NamespaceMember addMember(Long namespaceId, String userId, NamespaceRole role, String operatorUserId) {
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        if (role == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.assignDirect");
        }

        if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId).isPresent()) {
            throw new DomainBadRequestException("error.namespace.member.alreadyExists");
        }

        NamespaceMember member = new NamespaceMember(namespaceId, userId, role);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long namespaceId, String userId, String operatorUserId) {
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));

        if (member.getRole() == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.remove");
        }

        namespaceMemberRepository.deleteByNamespaceIdAndUserId(namespaceId, userId);
    }

    @Transactional
    public NamespaceMember updateMemberRole(Long namespaceId, String userId, NamespaceRole newRole, String operatorUserId) {
        namespaceService.assertAdminOrOwner(namespaceId, operatorUserId);

        if (newRole == NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.member.owner.setDirect");
        }

        NamespaceMember member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.member.notFound"));

        member.setRole(newRole);
        return namespaceMemberRepository.save(member);
    }

    @Transactional
    public void transferOwnership(Long namespaceId, String currentOwnerId, String newOwnerId) {
        NamespaceMember currentOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.owner.current.notFound"));

        if (currentOwner.getRole() != NamespaceRole.OWNER) {
            throw new DomainBadRequestException("error.namespace.owner.current.invalid");
        }

        NamespaceMember newOwner = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.owner.new.notFound"));

        currentOwner.setRole(NamespaceRole.ADMIN);
        newOwner.setRole(NamespaceRole.OWNER);

        namespaceMemberRepository.save(currentOwner);
        namespaceMemberRepository.save(newOwner);
    }

    public Optional<NamespaceRole> getMemberRole(Long namespaceId, String userId) {
        return namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .map(NamespaceMember::getRole);
    }

    public Page<NamespaceMember> listMembers(Long namespaceId, Pageable pageable) {
        return namespaceMemberRepository.findByNamespaceId(namespaceId, pageable);
    }
}
