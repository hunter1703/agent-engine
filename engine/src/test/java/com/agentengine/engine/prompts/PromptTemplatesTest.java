package com.agentengine.engine.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.utils.TemplateUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

  @Test
  void jsonProtocolPromptRendersSchemaAndThoughts() {
    final String rendered = TemplateUtils.renderTemplateForName(
        "shared/protocol/json.txt",
        Map.of("thoughtsEnabled", true, "response_schema", "{schema}"));

    assertThat(rendered).contains("{schema}");
    assertThat(rendered).contains("\"finalAnswer\"");
    assertThat(rendered).contains("\"thoughts\"");
  }

  @Test
  void textProtocolPromptRendersThoughtTags() {
    final String rendered = TemplateUtils.renderTemplateForName(
        "shared/protocol/text.txt",
        Map.of(
            "thoughtsEnabled", true,
            "thoughtsStartTag", "<think>",
            "thoughtsEndTag", "</think>"));

    assertThat(rendered).contains("<think>");
    assertThat(rendered).contains("</think>");
    assertThat(rendered).contains("plain text");
  }

  @Test
  void complexityRouterPromptDefinesJsonSchema() {
    final String rendered = TemplateUtils.renderTemplateForName(
        "hybrid/complexity_router.txt",
        Map.of());

    assertThat(rendered).contains("\"complex\"");
    assertThat(rendered).contains("JSON");
  }
}
