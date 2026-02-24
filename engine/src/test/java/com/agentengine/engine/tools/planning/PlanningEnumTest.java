package com.agentengine.engine.tools.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import org.junit.jupiter.api.Test;

class PlanningEnumTest {

  @Test
  void taskStatusValueOfOrDefaultHandlesUnknowns() {
    assertThat(TaskStatus.valueOfOrDefault("todo")).isEqualTo(TaskStatus.TODO);
    assertThat(TaskStatus.valueOfOrDefault("IN_PROGRESS")).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(TaskStatus.valueOfOrDefault("bad")).isEqualTo(TaskStatus.UNKNOWN);
  }

  @Test
  void planStatusValueOfOrDefaultHandlesUnknowns() {
    assertThat(PlanStatus.valueOfOrDefault("done")).isEqualTo(PlanStatus.DONE);
    assertThat(PlanStatus.valueOfOrDefault("ABANDONED")).isEqualTo(PlanStatus.ABANDONED);
    assertThat(PlanStatus.valueOfOrDefault("oops")).isEqualTo(PlanStatus.UNKNOWN);
  }
}
