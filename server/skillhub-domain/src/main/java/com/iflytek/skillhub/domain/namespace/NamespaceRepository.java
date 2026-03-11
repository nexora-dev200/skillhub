package com.iflytek.skillhub.domain.namespace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NamespaceRepository {
    Optional<Namespace> findById(Long id);
    Optional<Namespace> findBySlug(String slug);
    Page<Namespace> findByStatus(NamespaceStatus status, Pageable pageable);
    Namespace save(Namespace namespace);
}
