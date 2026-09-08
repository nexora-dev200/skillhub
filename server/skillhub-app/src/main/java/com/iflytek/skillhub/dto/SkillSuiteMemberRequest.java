package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

/** Exact Skill coordinate selected for a Suite draft. */
public record SkillSuiteMemberRequest(
        @NotBlank String namespace,
        @NotBlank String slug,
        @NotBlank String version
) {
}
