package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextConfigTest {

  @Test
  void contextConfigsSetDefaultTypes() {
    LastNContextConfig lastN = new LastNContextConfig();
    SummarizingContextConfig summarizing = new SummarizingContextConfig();

    assertThat(lastN.getType()).isEqualTo("last_n");
    assertThat(summarizing.getType()).isEqualTo("summarize");
  }

  @Test
  void contextConfigsAllowOverrides() {
    LastNContextConfig lastN = new LastNContextConfig();
    lastN.setKeepLast(3);
    lastN.setType("custom");

    assertThat(lastN.getKeepLast()).isEqualTo(3);
    assertThat(lastN.getType()).isEqualTo("custom");
  }
}

