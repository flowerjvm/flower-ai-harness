package io.github.parkkevinsb.flower.ai.harness.finding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain-neutral structured finding produced by a harness.
 */
public record AiFinding(
        String code,
        AiFindingSeverity severity,
        String message,
        String evidence,
        String location,
        Map<String, String> attributes
) {

    public AiFinding {
        code = requireText(code, "code");
        Objects.requireNonNull(severity, "severity must not be null");
        message = requireText(message, "message");
        evidence = evidence == null ? "" : evidence;
        location = location == null ? "" : location;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AiFinding of(String code, AiFindingSeverity severity, String message) {
        return new AiFinding(code, severity, message, "", "", Map.of());
    }

    public AiFinding withEvidence(String evidence) {
        return new AiFinding(code, severity, message, evidence, location, attributes);
    }

    public AiFinding withLocation(String location) {
        return new AiFinding(code, severity, message, evidence, location, attributes);
    }

    public AiFinding withAttribute(String key, String value) {
        key = requireText(key, "key");
        Objects.requireNonNull(value, "value must not be null");
        Map<String, String> next = new LinkedHashMap<>(attributes);
        next.put(key, value);
        return new AiFinding(code, severity, message, evidence, location, next);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
