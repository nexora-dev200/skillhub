package com.iflytek.skillhub.domain.skill.validation;

import org.springframework.stereotype.Component;

@Component
public class NoOpPrePublishValidator implements PrePublishValidator {

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        return ValidationResult.pass();
    }
}
