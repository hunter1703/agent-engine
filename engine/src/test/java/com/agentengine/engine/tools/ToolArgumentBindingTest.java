package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.tools.planning.beans.Task;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolArgumentBindingTest {

  @Test
  void bindsListOfObjects() {
    TaskListTool tool = new TaskListTool();

    Map<String, Object> args =
        Map.of(
            "tasks",
            List.of(
                Map.of(
                    "name",
                    "Task A",
                    "goal",
                    "Goal A",
                    "parent_id",
                    "parent-1")));

    tool.runAsync(args, null).blockingGet();

    assertThat(tool.received()).hasSize(1);
    assertThat(tool.received().get(0).getParentId()).isEqualTo("parent-1");
  }

  @Test
  void bindsMapOfObjects() {
    TaskMapTool tool = new TaskMapTool();

    Map<String, Object> args =
        Map.of(
            "task_map",
            Map.of(
                "first",
                Map.of(
                    "name",
                    "Task B",
                    "goal",
                    "Goal B",
                    "parent_id",
                    "parent-2")));

    tool.runAsync(args, null).blockingGet();

    assertThat(tool.received().get("first")).isNotNull();
    assertThat(tool.received().get("first").getParentId()).isEqualTo("parent-2");
  }

  private static final class TaskListTool extends Tool {
    private static final ToolDescriptor DESCRIPTOR =
        new ToolDescriptor("capture_tasks", "Capture tasks list", List.of(ALL), Map.of());
    private List<Task> received;

    private TaskListTool() {
      super(DESCRIPTOR);
    }

    @ToolSchema(name = "capture_tasks", description = "Capture tasks list")
    public Map<String, Object> execute(
        @ToolSchema(name = "tasks", description = "Tasks list") List<Task> tasks) {
      this.received = tasks;
      return Map.of("count", tasks.size());
    }

    private List<Task> received() {
      return received;
    }
  }

  private static final class TaskMapTool extends Tool {
    private static final ToolDescriptor DESCRIPTOR =
        new ToolDescriptor("capture_task_map", "Capture tasks map", List.of(ALL), Map.of());
    private Map<String, Task> received;

    private TaskMapTool() {
      super(DESCRIPTOR);
    }

    @ToolSchema(name = "capture_task_map", description = "Capture tasks map")
    public Map<String, Object> execute(
        @ToolSchema(name = "task_map", description = "Tasks map")
            Map<String, Task> taskMap) {
      this.received = taskMap;
      return Map.of("size", taskMap.size());
    }

    private Map<String, Task> received() {
      return received;
    }
  }
}
