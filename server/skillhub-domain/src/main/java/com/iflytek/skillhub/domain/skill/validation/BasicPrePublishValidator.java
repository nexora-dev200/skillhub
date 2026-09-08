package com.iflytek.skillhub.domain.skill.validation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default pre-publish validator that scans text-like package files for likely secrets and
 * accidental real credentials.
 */
@Component
public class BasicPrePublishValidator implements PrePublishValidator {

    private static final Pattern ASSIGNMENT_WITH_SENSITIVE_KEY = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?key|secret|password|token)\\s*[:=]\\s*(.+)$"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern BARE_LITERAL = Pattern.compile("[A-Za-z0-9_\\-]{12,}");
    private static final Set<String> PLACEHOLDER_MARKERS = Set.of(
            "your", "example", "sample", "placeholder", "changeme", "replace", "dummy",
            "mock", "test", "fake", "todo", "xxx", "redacted");
    private static final List<SecretRule> SECRET_RULES = List.of(
            new SecretRule(Pattern.compile("(AKIA[0-9A-Z]{16})"), 1, "cloud access key"),
            new SecretRule(Pattern.compile("(ghp_[A-Za-z0-9]{20,})"), 1, "GitHub token"),
            new SecretRule(Pattern.compile("(sk-[A-Za-z0-9]{20,})"), 1, "API key"),
            new SecretRule(ASSIGNMENT_WITH_SENSITIVE_KEY, 0, "secret or token")
    );

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        List<String> warnings = new ArrayList<>();

        for (PackageEntry entry : context.entries()) {
            if (!isTextLike(entry.path())) {
                continue;
            }
            String content = new String(entry.content(), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                for (SecretRule rule : SECRET_RULES) {
                    Matcher matcher = rule.pattern().matcher(line);
                    if (!matcher.find()) {
                        continue;
                    }
                    String matchedValue = extractMatchedValue(line, matcher, rule);
                    if (matchedValue == null) {
                        continue;
                    }
                    if (isPlaceholderValue(matchedValue)) {
                        continue;
                    }
                    warnings.add(entry.path()
                            + " line " + (i + 1)
                            + " contains a value that looks like a "
                            + rule.label()
                            + ". Replace real credentials with placeholders before publishing.");
                    break;
                }
            }
        }

        return warnings.isEmpty() ? ValidationResult.pass() : ValidationResult.warn(warnings);
    }

    private boolean isTextLike(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".md") || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".json") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".js") || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".py") || lowerPath.endsWith(".sh") || lowerPath.endsWith(".svg")
                || lowerPath.endsWith(".html") || lowerPath.endsWith(".css") || lowerPath.endsWith(".csv")
                || lowerPath.endsWith(".toml") || lowerPath.endsWith(".xml") || lowerPath.endsWith(".ini")
                || lowerPath.endsWith(".cfg") || lowerPath.endsWith(".env")
                || lowerPath.endsWith(".rb") || lowerPath.endsWith(".go") || lowerPath.endsWith(".rs")
                || lowerPath.endsWith(".java") || lowerPath.endsWith(".kt") || lowerPath.endsWith(".lua")
                || lowerPath.endsWith(".sql") || lowerPath.endsWith(".r")
                || lowerPath.endsWith(".bat") || lowerPath.endsWith(".ps1")
                || lowerPath.endsWith(".zsh") || lowerPath.endsWith(".bash");
    }

    private boolean isPlaceholderValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(normalizedValue::contains)
                || value.chars().allMatch(ch -> ch == 'x' || ch == 'X' || ch == '*' || ch == '-');
    }

    private String extractMatchedValue(String line, Matcher matcher, SecretRule rule) {
        if (rule.valueGroup() > 0) {
            return matcher.group(rule.valueGroup());
        }

        Matcher assignmentMatcher = ASSIGNMENT_WITH_SENSITIVE_KEY.matcher(line);
        if (!assignmentMatcher.find()) {
            return null;
        }

        String rawValue = assignmentMatcher.group(2).trim();
        if (rawValue.isBlank()) {
            return null;
        }

        String quotedLiteral = extractQuotedLiteral(rawValue);
        if (quotedLiteral != null) {
            return quotedLiteral;
        }

        rawValue = stripInlineComment(rawValue);
        if (rawValue.isBlank()) {
            return null;
        }

        quotedLiteral = extractQuotedLiteral(rawValue);
        if (quotedLiteral != null) {
            return quotedLiteral;
        }

        if (looksLikeExpression(rawValue) || IDENTIFIER.matcher(rawValue).matches()) {
            return null;
        }

        return BARE_LITERAL.matcher(rawValue).matches() ? rawValue : null;
    }

    private String extractQuotedLiteral(String rawValue) {
        if (rawValue.length() < 2) {
            return null;
        }

        char quote = rawValue.charAt(0);
        if (quote != '\'' && quote != '"') {
            return null;
        }

        boolean escaped = false;
        for (int i = 1; i < rawValue.length(); i++) {
            char current = rawValue.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == quote) {
                return hasOnlyTrailingSyntax(rawValue, i + 1) ? rawValue.substring(1, i) : null;
            }
        }
        return null;
    }

    private boolean hasOnlyTrailingSyntax(String rawValue, int startIndex) {
        for (int i = startIndex; i < rawValue.length(); i++) {
            char current = rawValue.charAt(i);
            if (Character.isWhitespace(current) || isTrailingDelimiter(current)) {
                continue;
            }
            if (current == '#') {
                return true;
            }
            return current == '/' && i + 1 < rawValue.length() && rawValue.charAt(i + 1) == '/';
        }
        return true;
    }

    private boolean isTrailingDelimiter(char value) {
        return value == ',' || value == ';' || value == ')' || value == '}' || value == ']';
    }

    private String stripInlineComment(String rawValue) {
        int hashIndex = rawValue.indexOf('#');
        if (hashIndex >= 0) {
            return rawValue.substring(0, hashIndex).trim();
        }
        return rawValue;
    }

    private boolean looksLikeExpression(String rawValue) {
        return rawValue.contains("(")
                || rawValue.contains(")")
                || rawValue.contains(".")
                || rawValue.contains("[")
                || rawValue.contains("]")
                || rawValue.contains("{")
                || rawValue.contains("}")
                || rawValue.contains(",")
                || rawValue.contains(" ")
                || rawValue.contains("+")
                || rawValue.contains("/");
    }

    private record SecretRule(Pattern pattern, int valueGroup, String label) {}
}
