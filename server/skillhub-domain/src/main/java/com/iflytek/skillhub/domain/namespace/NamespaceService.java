package com.iflytek.skillhub.domain.namespace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NamespaceService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;

    public NamespaceService(NamespaceRepository namespaceRepository,
                           NamespaceMemberRepository namespaceMemberRepository) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
    }

    @Transactional
    public Namespace createNamespace(String slug, String displayName, String description, Long creatorUserId) {
        SlugValidator.validate(slug);

        if (namespaceRepository.findBySlug(slug).isPresent()) {
            throw new IllegalArgumentException("Namespace with slug '" + slug + "' already exists");
        }

        Namespace namespace = new Namespace(slug, displayName, creatorUserId);
        namespace.setDescription(description);
        namespace.setType(NamespaceType.TEAM);
        namespace = namespaceRepository.save(namespace);

        NamespaceMember ownerMember = new NamespaceMember(namespace.getId(), creatorUserId, NamespaceRole.OWNER);
        namespaceMemberRepository.save(ownerMember);

        return namespace;
    }

    @Transactional
    public Namespace updateNamespace(Long namespaceId, String displayName, String description, String avatarUrl) {
        Namespace namespace = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found with id: " + namespaceId));

        if (displayName != null) {
            namespace.setDisplayName(displayName);
        }
        if (description != null) {
            namespace.setDescription(description);
        }
        if (avatarUrl != null) {
            namespace.setAvatarUrl(avatarUrl);
        }

        return namespaceRepository.save(namespace);
    }

    public Namespace getNamespaceBySlug(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Namespace not found with slug: " + slug));
    }
}
