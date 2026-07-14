package io.github.flowerjvm.flower.ai.harness.validator.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.validate.AiSchemaValidator;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationError;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Jackson-backed validator that parses model raw text into a Java type.
 */
public final class JacksonPojoSchemaValidator<T> implements AiSchemaValidator<T> {

    private final ObjectReader reader;

    public JacksonPojoSchemaValidator(Class<T> type, ObjectMapper mapper) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        ObjectMapper configured = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
        this.reader = configured.readerFor(type);
    }

    @Override
    public ValidationResult<T> validate(AiModelResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        try {
            T value = reader.readValue(response.rawText());
            return new ValidationResult.Valid<>(value);
        } catch (JsonMappingException ex) {
            String message = ex.getOriginalMessage();
            if (looksMalformedJson(ex, message)) {
                return invalid("MALFORMED_JSON", "", message);
            }
            return invalid("STRUCTURE_MISMATCH", path(ex), message);
        } catch (JsonProcessingException ex) {
            return invalid("MALFORMED_JSON", "", ex.getOriginalMessage());
        } catch (IllegalArgumentException ex) {
            return invalid("INVALID_RESPONSE", "", ex.getMessage());
        }
    }

    private static <T> ValidationResult<T> invalid(String code, String path, String message) {
        return new ValidationResult.Invalid<>(List.of(new ValidationError(path, code, message)));
    }

    private static String path(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(JacksonPojoSchemaValidator::referenceName)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining("."));
    }

    private static boolean looksMalformedJson(JsonMappingException ex, String message) {
        return message != null && message.contains("Unexpected end-of-input");
    }

    private static String referenceName(JsonMappingException.Reference reference) {
        if (reference.getFieldName() != null) {
            return reference.getFieldName();
        }
        if (reference.getIndex() >= 0) {
            return "[" + reference.getIndex() + "]";
        }
        return "";
    }
}
