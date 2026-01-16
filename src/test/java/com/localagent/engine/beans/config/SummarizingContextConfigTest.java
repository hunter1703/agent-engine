package com.localagent.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SummarizingContextConfigTest {

    @Test
    void summarizingConfigStoresThresholdsAndModel() {
        SummarizingContextConfig config = new SummarizingContextConfig();
        config.setTriggerThreshold(0.3);
        config.setRecencyThreshold(0.2);
        config.setSummarizerModel("summarizer");

        assertThat(config.getTriggerThreshold()).isEqualTo(0.3);
        assertThat(config.getRecencyThreshold()).isEqualTo(0.2);
        assertThat(config.getSummarizerModel()).isEqualTo("summarizer");
    }
}
