package io.github.flowerjvm.flower.ai.harness.validate;

import java.util.List;
import java.util.Objects;

/**
 * Result of validating a model response against an expected structure.
 */
public sealed interface ValidationResult<T> permits ValidationResult.Valid, ValidationResult.Invalid {

    boolean isValid();

    record Valid<T>(T value) implements ValidationResult<T> {

        public Valid {
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }

    record Invalid<T>(List<ValidationError> errors) implements ValidationResult<T> {

        public Invalid {
            Objects.requireNonNull(errors, "errors must not be null");
            errors = List.copyOf(errors);
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }

        @Override
        public boolean isValid() {
            return false;
        }
    }
}
