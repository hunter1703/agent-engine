package com.agentengine.engine.tools.verification;

import static org.junit.jupiter.api.Assertions.*;

import com.agentengine.engine.tools.UserClarificationTool;
import com.agentengine.engine.tools.echo.EchoTool;
import com.agentengine.engine.tools.planning.AddTaskTool;
import com.agentengine.engine.tools.planning.CreatePlanTool;
import com.agentengine.engine.tools.planning.FinishPlanTool;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.UpdatePlanTool;
import com.agentengine.engine.tools.planning.UpdateTaskInfoTool;
import com.agentengine.engine.tools.planning.ViewPlanTool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.agentengine.engine.tools.shell.ShellCommandTool;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ToolVerificationTest {

    private ToolContext toolContext;
    private Session session;

    @BeforeEach
    void setUp() {
        session = Session.builder("test-session")
            .appName("test-app")
            .userId("test-user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
        
        InvocationContext invocationContext = InvocationContext.builder()
            .session(session)
            .build();
            
        toolContext = ToolContext.builder(invocationContext).build();
    }

    @Test
    void testEchoTool() {
        EchoTool tool = new EchoTool();
        
        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("echo", decl.name().get());
        Schema params = decl.parameters().get();
        java.util.Map<String, Schema> props = params.properties().get();
        assertTrue(props.containsKey("text"));
        assertTrue(props.containsKey("prefix"));
        assertTrue(params.required().get().contains("text"));

        // 2. Verify Invocation
        Map<String, Object> args = Map.of("text", "hello", "prefix", "TEST: ");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("TEST: hello", result.get("output"));
    }

    @Test
    void testShellCommandTool() {
        ShellCommandTool tool = new ShellCommandTool();
        
        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("run_cmd", decl.name().get());
        Schema params = decl.parameters().get();
        java.util.Map<String, Schema> props = params.properties().get();
        assertTrue(props.containsKey("command"));

        // 2. Verify Invocation
        Map<String, Object> args = Map.of("command", "echo 'a' && echo 'b'");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("a\nb", result.get("output").toString().trim());
    }

    @Test
    void testAddTaskTool() {
        AddTaskTool tool = new AddTaskTool();
        
        // Setup state with a plan
        Plan plan = new Plan("My Plan", "Goal");
        session.state().put(PlanningUtils.PLAN_STATE_KEY, plan);

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("add_task", decl.name().get());
        Schema params = decl.parameters().get();
        java.util.Map<String, Schema> props = params.properties().get();
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("goal"));

        // 2. Verify Invocation
        Map<String, Object> args = Map.of("name", "New Task", "goal", "Do something");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("success", result.get("status"));
        assertEquals(1, plan.getTasks().size());
        assertEquals("New Task", plan.getTasks().get(0).getName());
    }

    @Test
    void testCreatePlanTool() {
        CreatePlanTool tool = new CreatePlanTool();

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("create_plan", decl.name().get());
        Schema params = decl.parameters().get();
        java.util.Map<String, Schema> props = params.properties().get();
        assertTrue(props.containsKey("tasks"));
        Schema tasksSchema = props.get("tasks");
        assertEquals("ARRAY", tasksSchema.type().get().toString());

        // 2. Verify Invocation with complex arguments (List of Beans)
        Map<String, Object> task1Data = Map.of("name", "T1", "goal", "G1");
        Map<String, Object> task2Data = Map.of("name", "T2", "goal", "G2");
        Map<String, Object> args = Map.of(
            "title", "Initial Plan",
            "goal", "Overall Goal",
            "tasks", List.of(task1Data, task2Data)
        );

        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("success", result.get("status"));
        
        Plan plan = (Plan) session.state().get(PlanningUtils.PLAN_STATE_KEY);
        assertNotNull(plan);
        assertEquals(2, plan.getTasks().size());
        assertEquals("T1", plan.getTasks().get(0).getName());
    }
    @Test
    void testFinishPlanTool() {
        FinishPlanTool tool = new FinishPlanTool();

        // Setup state with a plan
        Plan plan = new Plan("My Plan", "Goal");
        session.state().put(PlanningUtils.PLAN_STATE_KEY, plan);

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("finish_plan", decl.name().get());

        // 2. Verify Invocation
        Map<String, Object> args = Map.of("status", "done", "result", "Mission accomplished");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("success", result.get("status"));
        assertEquals("Mission accomplished", plan.getResult());
    }

    @Test
    void testUpdatePlanTool() {
        UpdatePlanTool tool = new UpdatePlanTool();

        // Setup state with a plan
        Plan plan = new Plan("Old Title", "Old Goal");
        session.state().put(PlanningUtils.PLAN_STATE_KEY, plan);

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("update_plan", decl.name().get());

        // 2. Verify Invocation
        Map<String, Object> args = Map.of("title", "New Title");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("success", result.get("status"));
        assertEquals("New Title", plan.getTitle());
    }

    @Test
    void testUpdateTaskTool() {
        UpdateTaskInfoTool tool = new UpdateTaskInfoTool();

        // Setup state with a plan and task
        Task task = new Task("Task1", "Goal1");
        Plan plan = new Plan("Plan", "Goal");
        plan.setTasks(new ArrayList<>(List.of(task)));
        session.state().put(PlanningUtils.PLAN_STATE_KEY, plan);

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("update_task_info", decl.name().get());

        // 2. Verify Invocation
        Map<String, Object> args = Map.of(
            "task_id", task.getTaskId(),
            "status", "DONE",
            "result", "Finished"
        );
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("success", result.get("status"));
        assertEquals("done", task.getStatus().getValue());
    }

    @Test
    void testViewPlanTool() {
        ViewPlanTool tool = new ViewPlanTool();

        // Setup state with a plan
        Plan plan = new Plan("Plan", "Goal");
        session.state().put(PlanningUtils.PLAN_STATE_KEY, plan);

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("view_plan", decl.name().get());

        // 2. Verify Invocation
        Map<String, Object> args = Map.of();
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertTrue(result.containsKey("title"));
    }

    @Test
    void testUserClarificationTool() {
        UserClarificationTool tool = new UserClarificationTool();

        // 1. Verify Schema
        FunctionDeclaration decl = tool.declaration().get();
        assertEquals("user_clarification", decl.name().get());

        // 2. Verify Invocation
        Map<String, Object> args = Map.of("question", "What is your favorite color?");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        assertEquals("What is your favorite color?", result.get("clarification"));
    }

    @Test
    void testPlanRetrievalFromMapState() {
        // Simulate a plan that has been reloaded as a Map (e.g. from session state)
        String planId = "plan-123";
        String taskId = "task-456";
        
        Map<String, Object> taskMap = new java.util.HashMap<>();
        taskMap.put("taskId", taskId);
        taskMap.put("name", "New Task");
        taskMap.put("goal", "Task Goal");
        taskMap.put("status", "TODO");
        
        Map<String, Object> planMap = new java.util.HashMap<>();
        planMap.put("planId", planId);
        planMap.put("title", "Persistence Test");
        planMap.put("goal", "Goal");
        planMap.put("tasks", List.of(taskMap));
        
        session.state().put(PlanningUtils.PLAN_STATE_KEY, planMap);
        
        // Verify PlanningUtils can still find it and IDs are mapped correctly
        Plan retrieved = PlanningUtils.getCurrentPlan(toolContext);
        assertNotNull(retrieved);
        assertEquals(planId, retrieved.getPlanId());
        assertEquals(1, retrieved.getTasks().size());
        assertEquals(taskId, retrieved.getTasks().get(0).getTaskId());
        
        // Verify a tool can still use it (e.g. UpdateTaskTool uses the ID to find the task)
        UpdateTaskInfoTool tool = new UpdateTaskInfoTool();
        Map<String, Object> args = Map.of("task_id", taskId, "status", "in_progress");
        Map<String, Object> result = tool.runAsync(args, toolContext).blockingGet();
        
        assertEquals("success", result.get("status"));
        Plan finalPlan = PlanningUtils.getCurrentPlan(toolContext);
        assertEquals(TaskStatus.IN_PROGRESS, finalPlan.getTasks().get(0).getStatus());
    }
}
