package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/namespaces")
public class NamespaceController {

    private final NamespaceService namespaceService;
    private final NamespaceMemberService namespaceMemberService;
    private final NamespaceRepository namespaceRepository;

    public NamespaceController(NamespaceService namespaceService,
                              NamespaceMemberService namespaceMemberService,
                              NamespaceRepository namespaceRepository) {
        this.namespaceService = namespaceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping
    public Map<String, Object> listNamespaces(Pageable pageable) {
        Page<Namespace> namespaces = namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, pageable);
        Page<NamespaceResponse> response = namespaces.map(NamespaceResponse::from);
        return Map.of("code", 0, "data", response);
    }

    @GetMapping("/{slug}")
    public Map<String, Object> getNamespace(@PathVariable String slug) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        return Map.of("code", 0, "data", NamespaceResponse.from(namespace));
    }

    @PostMapping
    public Map<String, Object> createNamespace(
            @Valid @RequestBody NamespaceRequest request,
            @AuthenticationPrincipal Long userId) {
        Namespace namespace = namespaceService.createNamespace(
                request.slug(),
                request.displayName(),
                request.description(),
                userId
        );
        return Map.of("code", 0, "data", NamespaceResponse.from(namespace));
    }

    @PutMapping("/{slug}")
    public Map<String, Object> updateNamespace(
            @PathVariable String slug,
            @RequestBody NamespaceRequest request) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Namespace updated = namespaceService.updateNamespace(
                namespace.getId(),
                request.displayName(),
                request.description(),
                null
        );
        return Map.of("code", 0, "data", NamespaceResponse.from(updated));
    }

    @GetMapping("/{slug}/members")
    public Map<String, Object> listMembers(@PathVariable String slug, Pageable pageable) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Page<NamespaceMember> members = namespaceMemberService.listMembers(namespace.getId(), pageable);
        Page<MemberResponse> response = members.map(MemberResponse::from);
        return Map.of("code", 0, "data", response);
    }

    @PostMapping("/{slug}/members")
    public Map<String, Object> addMember(
            @PathVariable String slug,
            @Valid @RequestBody MemberRequest request) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        NamespaceMember member = namespaceMemberService.addMember(
                namespace.getId(),
                request.userId(),
                request.role()
        );
        return Map.of("code", 0, "data", MemberResponse.from(member));
    }

    @DeleteMapping("/{slug}/members/{userId}")
    public Map<String, Object> removeMember(
            @PathVariable String slug,
            @PathVariable Long userId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceMemberService.removeMember(namespace.getId(), userId);
        return Map.of("code", 0, "data", "Member removed successfully");
    }

    @PutMapping("/{slug}/members/{userId}/role")
    public Map<String, Object> updateMemberRole(
            @PathVariable String slug,
            @PathVariable Long userId,
            @RequestBody Map<String, NamespaceRole> body) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        NamespaceRole newRole = body.get("role");
        if (newRole == null) {
            throw new IllegalArgumentException("Role is required");
        }
        NamespaceMember member = namespaceMemberService.updateMemberRole(
                namespace.getId(),
                userId,
                newRole
        );
        return Map.of("code", 0, "data", MemberResponse.from(member));
    }
}
