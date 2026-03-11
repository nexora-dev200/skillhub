package com.iflytek.skillhub.search;

public record SkillSearchDocument(
        Long skillId,
        Long namespaceId,
        String namespaceSlug,
        Long ownerId,
        String title,
        String summary,
        String keywords,
        String searchText,
        String visibility,
        String status
) {}
