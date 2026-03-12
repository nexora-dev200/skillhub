package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;

public record SkillVersionDetailResponse(
        Long id,
        String version,
        String status,
        String changelog,
        int fileCount,
        long totalSize,
        LocalDateTime publishedAt,
        String parsedMetadataJson,
        String manifestJson
) {}
