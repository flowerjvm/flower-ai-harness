package io.github.flowerjvm.flower.ai.harness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaVersionTest {

    @Test
    void runsOnJava21OrNewer() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }
}
