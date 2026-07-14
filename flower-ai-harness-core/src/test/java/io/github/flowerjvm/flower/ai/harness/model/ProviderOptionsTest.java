package io.github.flowerjvm.flower.ai.harness.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderOptionsTest {

    @Test
    void emptyReturnsSharedEmptyOptions() {
        ProviderOptions options = ProviderOptions.empty();

        assertThat(options.asMap()).isEmpty();
        assertThat(options.get("missing")).isEmpty();
    }

    @Test
    void copiesSourceMap() {
        Map<String, Object> source = new HashMap<>();
        source.put("temperature", 0.2);

        ProviderOptions options = ProviderOptions.of(source);
        source.put("temperature", 1.0);

        assertThat(options.get("temperature")).contains(0.2);
    }

    @Test
    void exposesUnmodifiableMap() {
        ProviderOptions options = ProviderOptions.empty().with("jsonMode", true);

        assertThatThrownBy(() -> options.asMap().put("other", false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withReturnsNewOptionsWithoutMutatingOriginal() {
        ProviderOptions original = ProviderOptions.empty();
        ProviderOptions next = original.with("topP", 0.9);

        assertThat(original.get("topP")).isEmpty();
        assertThat(next.get("topP")).contains(0.9);
    }

    @Test
    void rejectsNullKeysAndValues() {
        assertThatThrownBy(() -> ProviderOptions.empty().with(null, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProviderOptions.empty().with("key", null))
                .isInstanceOf(NullPointerException.class);
    }
}
