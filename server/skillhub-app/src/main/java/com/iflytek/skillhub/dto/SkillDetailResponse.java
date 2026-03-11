package com.iflytek.skillhub.dto;

public record SkillDetailResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        String latestVersion,
        String namespace
) {}
