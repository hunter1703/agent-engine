package com.agentengine.cli.beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestTest {

  @Test
  void requestTypesDefaultFromSubclass() {
    InvokeAgentRequest invoke = new InvokeAgentRequest();
    BuildPromptRequest prompt = new BuildPromptRequestTest();

    assertThat(invoke.getType()).isEqualTo("INVOKE_AGENT");
    assertThat(prompt.getType()).isEqualTo("BUILD_PROMPT");
  }

  @Test
  void invokeRequestStoresUserMessageAndId() {
    InvokeAgentRequest invoke = new InvokeAgentRequest();
    invoke.setId("req-1");
    invoke.setUserMessage("hello");

    assertThat(invoke.getId()).isEqualTo("req-1");
    assertThat(invoke.getUserMessage()).isEqualTo("hello");
  }

  private static final class BuildPromptRequestTest extends BuildPromptRequest {
    private BuildPromptRequestTest() {
      super();
    }
  }
}
