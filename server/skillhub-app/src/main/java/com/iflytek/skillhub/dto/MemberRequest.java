package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import jakarta.validation.constraints.NotNull;

public record MemberRequest(
        @NotNull(message = "User ID cannot be null")
        Long userId,

        @NotNull(message = "Role cannot be null")
        NamespaceRole role
) {}
