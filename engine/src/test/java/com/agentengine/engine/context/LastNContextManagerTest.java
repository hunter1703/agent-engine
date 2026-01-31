package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class LastNContextManagerTest {

  @Test
  void buildPromptKeepsRecentCharacters() {
    final List<Content> contents = List.of(
        Content.builder().role("user").parts(Part.builder().text("first").build()).build(),
        Content.builder().role("user").parts(Part.builder().text("second").build()).build());

    final LastNContextManager contextManager = new LastNContextManager(1);

    final List<Content> prompt = contextManager.getPromptBuilder().apply(contents);

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().text()).contains("trimmed conversation");
    assertThat(prompt.get(1).text()).isEqualTo("ond");
  }
}
