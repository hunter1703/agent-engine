package com.agentengine.engine.api.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UpdateTest {

  @Test
  void shouldCreateUpdateWhenOperationsProvided() {
    final Operation setName = Operation.set("name", "agent");

    final Update update = Update.of(setName, Operation.unset("description"));

    assertThat(update.operations()).hasSize(2);
    assertThat(update.operations().getFirst()).isEqualTo(setName);
  }

  @Test
  void shouldThrowWhenOperationsListNull() {
    assertThatThrownBy(() -> new Update(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldCreateUnsetOperationWhenFieldProvided() {
    final Operation operation = Operation.unset("field");

    assertThat(operation.field()).isEqualTo("field");
    assertThat(operation.type()).isEqualTo(OperationType.UNSET);
    assertThat(operation.value()).isNull();
  }
}
