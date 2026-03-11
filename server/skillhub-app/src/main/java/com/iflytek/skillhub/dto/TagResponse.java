package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillTag;

import java.time.LocalDateTime;

public record TagResponse(
        Long id,
        String tagName,
        Long versionId,
        LocalDateTime createdAt
) {
    public static TagResponse from(SkillTag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getTagName(),
                tag.getVersionId(),
                tag.getCreatedAt()
        );
    }
}
