package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.Part;

import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class BaseContextManagerTest {

  @Test
  void returnsConfiguredPromptBuilder() {
    final UnaryOperator<List<Content>> builder = contents -> List
        .of(Content.builder().role("user").parts(Part.builder().text("trimmed").build()).build());
    final BaseContextManager contextManager = new BaseContextManager(builder);

    assertThat(contextManager.getPromptBuilder()).isSameAs(builder);
  }
}
