package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record TokenCreateRequest(
        @NotBlank(message = "{validation.token.name.notBlank}")
        String name,
        List<String> scopes
) {}
