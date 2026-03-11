package com.iflytek.skillhub.dto;

public record SkillSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        Long downloadCount,
        String latestVersion,
        String namespace
) {}
