package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UserClarificationAgentToolTestDefinition {

  @Test
  void clarifyFromUserReturnsClarificationPayload() {
    Map<String, Object> payload = UserClarificationTool.clarifyFromUser("Need input");

    assertThat(payload).containsEntry("clarification", "Need input");
  }
}
