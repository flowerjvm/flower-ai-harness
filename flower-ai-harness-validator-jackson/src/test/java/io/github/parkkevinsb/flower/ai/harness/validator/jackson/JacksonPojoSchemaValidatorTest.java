package io.github.parkkevinsb.flower.ai.harness.validator.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonPojoSchemaValidatorTest {

    private final JacksonPojoSchemaValidator<ReviewDraft> validator =
            new JacksonPojoSchemaValidator<>(ReviewDraft.class, new ObjectMapper());

    @Test
    void parsesValidJson() {
        ValidationResult<ReviewDraft> result = validator.validate(response("""
                {
                  "issues": [
                    { "code": "DOC_001", "message": "Missing section" }
                  ]
                }
                """));

        assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        ReviewDraft draft = ((ValidationResult.Valid<ReviewDraft>) result).value();
        assertThat(draft.issues()).containsExactly(new ReviewIssue("DOC_001", "Missing section"));
    }

    @Test
    void reportsWrongTypeAsStructureMismatch() {
        ValidationResult<ReviewDraft> result = validator.validate(response("""
                { "issues": "not-an-array" }
                """));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        ValidationResult.Invalid<ReviewDraft> invalid = (ValidationResult.Invalid<ReviewDraft>) result;
        assertThat(invalid.errors()).hasSize(1);
        assertThat(invalid.errors().get(0).code()).isEqualTo("STRUCTURE_MISMATCH");
        assertThat(invalid.errors().get(0).path()).contains("issues");
    }

    @Test
    void reportsMalformedJson() {
        ValidationResult<ReviewDraft> result = validator.validate(response("{ \"issues\": ["));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        ValidationResult.Invalid<ReviewDraft> invalid = (ValidationResult.Invalid<ReviewDraft>) result;
        assertThat(invalid.errors()).hasSize(1);
        assertThat(invalid.errors().get(0).code()).isEqualTo("MALFORMED_JSON");
    }

    @Test
    void reportsMissingCreatorProperty() {
        ValidationResult<ReviewDraft> result = validator.validate(response("{}"));

        assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        ValidationResult.Invalid<ReviewDraft> invalid = (ValidationResult.Invalid<ReviewDraft>) result;
        assertThat(invalid.errors()).hasSize(1);
        assertThat(invalid.errors().get(0).code()).isEqualTo("STRUCTURE_MISMATCH");
        assertThat(invalid.errors().get(0).message()).contains("issues");
    }

    private static AiModelResponse response(String rawText) {
        return new AiModelResponse(rawText, ModelId.parse("fake:model"), null);
    }

    private record ReviewDraft(List<ReviewIssue> issues) {
    }

    private record ReviewIssue(String code, String message) {
    }
}
