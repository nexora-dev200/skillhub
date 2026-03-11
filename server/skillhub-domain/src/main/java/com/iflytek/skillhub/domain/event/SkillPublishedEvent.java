package com.iflytek.skillhub.domain.event;

public record SkillPublishedEvent(Long skillId, Long versionId, Long publisherId) {}
