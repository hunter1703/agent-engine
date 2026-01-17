package com.agentengine.engine.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageTest {

  @Test
  void factoryMethodsSetRolesAndContent() {
    Message system = Message.system("sys");
    Message user = Message.user("user");
    Message tool = Message.tool("tool");
    Message assistant = Message.assistant("answer", "thoughts");

    assertThat(system.getRole()).isEqualTo(Role.SYSTEM);
    assertThat(user.getRole()).isEqualTo(Role.USER);
    assertThat(tool.getRole()).isEqualTo(Role.TOOL);
    assertThat(assistant.getRole()).isEqualTo(Role.ASSISTANT);
    assertThat(assistant.getThoughts()).isEqualTo("thoughts");
  }
}
