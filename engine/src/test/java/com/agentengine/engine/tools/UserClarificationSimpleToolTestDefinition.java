package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UserClarificationSimpleToolTestDefinition {

  @Test
  void clarifyFromUserReturnsClarificationPayload() {
    Map<String, Object> payload = UserClarificationTool.clarifyFromUser("Need input");

    assertThat(payload).containsEntry("clarification", "Need input");
  }

  @Test
  void clarifyFromUserIncludesOptionsWhenProvided() {
    Map<String, Object> payload =
        UserClarificationTool.clarifyFromUser(
            "Choose deployment target", java.util.List.of("Prod", "Stage", "Prod", " "));

    assertThat(payload).containsEntry("clarification", "Choose deployment target");
    assertThat(payload).containsEntry("options", java.util.List.of("Prod", "Stage"));
  }
}
