package com.iflytek.skillhub.infra.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanOptionsTest {

    @Test
    void disabled_usesSafeConsensusAndBalancedPolicyDefaults() {
        ScanOptions options = ScanOptions.disabled();

        assertThat(options.llmConsensusRuns()).isEqualTo(1);
        assertThat(options.policyPreset()).isEqualTo("balanced");
    }

    @Test
    void rejectsInvalidConsensusRuns() {
        assertThatThrownBy(() -> new ScanOptions(
                false, false, "anthropic", 0, "balanced", false, false, "", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("llmConsensusRuns");
    }

    @Test
    void rejectsUnknownPolicyPreset() {
        assertThatThrownBy(() -> new ScanOptions(
                false, false, "anthropic", 1, "custom", false, false, "", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyPreset");
    }
}
