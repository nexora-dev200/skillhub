package com.iflytek.skillhub.domain.namespace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface NamespaceMemberRepository {
    Optional<NamespaceMember> findByNamespaceIdAndUserId(Long namespaceId, Long userId);
    List<NamespaceMember> findByUserId(Long userId);
    Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable);
    NamespaceMember save(NamespaceMember member);
    void deleteByNamespaceIdAndUserId(Long namespaceId, Long userId);
}
