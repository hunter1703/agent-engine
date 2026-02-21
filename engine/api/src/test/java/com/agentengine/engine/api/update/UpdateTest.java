package com.agentengine.engine.api.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class UpdateTest {

  @Test
  void updateMakesDefensiveCopyOfOperations() {
    final ArrayList<Operation> operations = new ArrayList<>();
    operations.add(Operation.set("field", "value"));

    final Update update = new Update(operations);

    operations.add(Operation.unset("other"));

    assertThat(update.operations()).containsExactly(Operation.set("field", "value"));
    assertThatThrownBy(() -> update.operations().add(Operation.unset("extra")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void operationRequiresFieldAndType() {
    assertThatThrownBy(() -> new Operation(null, OperationType.SET, "value"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Operation("field", null, "value"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void updateRequiresOperations() {
    assertThatThrownBy(() -> new Update(null)).isInstanceOf(NullPointerException.class);
  }
}
