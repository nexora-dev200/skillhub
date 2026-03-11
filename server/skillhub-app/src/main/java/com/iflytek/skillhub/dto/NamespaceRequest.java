package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NamespaceRequest(
        @NotBlank(message = "Slug cannot be blank")
        @Size(min = 2, max = 64, message = "Slug must be between 2 and 64 characters")
        String slug,

        @NotBlank(message = "Display name cannot be blank")
        @Size(max = 128, message = "Display name must not exceed 128 characters")
        String displayName,

        @Size(max = 512, message = "Description must not exceed 512 characters")
        String description
) {}
