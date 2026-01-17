package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateUtilsTest {

  @Test
  void renderHandlesNullTemplateAndTrimsOutput() {
    assertThat(TemplateUtils.render(null, Map.of())).isEmpty();

    String rendered = TemplateUtils.render("  Hello {{ name }}  ", Map.of("name", "Ada"));

    assertThat(rendered).isEqualTo("Hello Ada");
  }

  @Test
  void renderProtocolUsesDefaultThoughtTag() {
    String template =
        "{% if thoughts_enabled %}<{{ thought_tag }}>ok</{{ thought_tag }}>{% endif %}";

    String rendered = TemplateUtils.renderProtocol(template, null, true);

    assertThat(rendered).isEqualTo("<think>ok</think>");
  }

  @Test
  void renderRouterInterpolatesValues() {
    String rendered = TemplateUtils.renderRouter("Route: {{ route }}", Map.of("route", "tool"));

    assertThat(rendered).isEqualTo("Route: tool");
  }
}

