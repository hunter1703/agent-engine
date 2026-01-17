package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateUtilsTest {

  @Test
  void renderHandlesNullTemplateAndTrimsOutput() {
    assertThat(TemplateUtils.renderTextTemplate(null, Map.of())).isEmpty();

    String rendered = TemplateUtils.renderTextTemplate("  Hello {{ name }}  ", Map.of("name", "Ada"));

    assertThat(rendered).isEqualTo("Hello Ada");
  }
}
