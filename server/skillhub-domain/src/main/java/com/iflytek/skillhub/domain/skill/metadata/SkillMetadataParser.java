package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

public class SkillMetadataParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    public SkillMetadata parse(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainBadRequestException("error.skill.metadata.content.empty");
        }

        String trimmedContent = content.trim();

        if (!trimmedContent.startsWith(FRONTMATTER_DELIMITER)) {
            throw new DomainBadRequestException("error.skill.metadata.frontmatter.missingStart");
        }

        int firstDelimiterEnd = trimmedContent.indexOf('\n', FRONTMATTER_DELIMITER.length());
        if (firstDelimiterEnd == -1) {
            throw new DomainBadRequestException("error.skill.metadata.frontmatter.missingContent");
        }

        int secondDelimiterStart = trimmedContent.indexOf(FRONTMATTER_DELIMITER, firstDelimiterEnd + 1);
        if (secondDelimiterStart == -1) {
            throw new DomainBadRequestException("error.skill.metadata.frontmatter.missingEnd");
        }

        String yamlContent = trimmedContent.substring(firstDelimiterEnd + 1, secondDelimiterStart).trim();
        String body = trimmedContent.substring(secondDelimiterStart + FRONTMATTER_DELIMITER.length()).trim();

        Map<String, Object> frontmatter;
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlContent);
            if (!(parsed instanceof Map)) {
                throw new DomainBadRequestException("error.skill.metadata.yaml.notMap");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            frontmatter = map;
        } catch (DomainBadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainBadRequestException("error.skill.metadata.yaml.invalid", e.getMessage());
        }

        String name = extractRequiredField(frontmatter, "name");
        String description = extractRequiredField(frontmatter, "description");
        String version = extractOptionalField(frontmatter, "version");

        return new SkillMetadata(name, description, version, body, frontmatter);
    }

    private String extractRequiredField(Map<String, Object> frontmatter, String fieldName) {
        Object value = frontmatter.get(fieldName);
        if (value == null) {
            throw new DomainBadRequestException("error.skill.metadata.requiredField.missing", fieldName);
        }
        return value.toString();
    }

    private String extractOptionalField(Map<String, Object> frontmatter, String fieldName) {
        Object value = frontmatter.get(fieldName);
        return value == null ? null : value.toString();
    }
}
