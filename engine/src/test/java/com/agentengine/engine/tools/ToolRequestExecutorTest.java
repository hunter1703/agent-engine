package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.ToolRequest;
import com.agentengine.engine.context.BaseContextBuilder;
import com.agentengine.engine.model.LLMModel;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.api.state.SessionStore;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.*;
import org.junit.jupiter.api.Test;

class ToolRequestExecutorTest {

    @Test
    void executeRunsToolPlanAndReturnsFinalAnswer() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";

        String toolAssistantPayload = """
                {"toolRequests":[{"id":"call-1","name":"echo","args":{"text":"hi"}}]}
                """;
        LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
                List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null, null)));

        List<Tool> tools = List.of(new EchoTool());
        Map<String, Tool> toolMap = new HashMap<>();
        tools.forEach(t -> toolMap.put(t.name(), t));

        BaseContextBuilder contextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", tools);
        AgenticToolExecutor executor = new ToolRequestExecutor(toolAssistantModel, contextBuilder, toolMap,
                sessionStore);
        CapturingListener listener = new CapturingListener();
        List<String> toolRequests = List
                .of("TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\",\"args\":{\"text\":\"hi\"}}");

        executor.executeRequests(sessionId, toolRequests, listener);

        assertThat(listener.toolPlans).hasSize(1);
        assertThat(listener.toolPlans.getFirst().iterator().next().name()).isEqualTo("echo");
        assertThat(listener.toolResults).hasSize(1);
        assertThat(listener.toolResults.values().iterator().next()).isEqualTo("hi");
    }

    @Test
    void executeHandlesUnknownTool() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";

        String toolAssistantPayload = """
                {"toolRequests":[{"id":"call-1","name":"unknown"}]}
                """;
        LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
                List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null, null)));

        BaseContextBuilder contextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
        AgenticToolExecutor executor = new ToolRequestExecutor(toolAssistantModel, contextBuilder, Map.of(),
                sessionStore);
        CapturingListener listener = new CapturingListener();
        List<String> toolRequests = List.of("TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"unknown\"}");
        executor.executeRequests(sessionId, toolRequests, listener);

        assertThat(listener.toolPlans).hasSize(1);
        assertThat(listener.toolResults).hasSize(1);
        assertThat(listener.toolResults.values().iterator().next()).contains("Unknown tool");
    }

    @Test
    void executeGracefullyHandlesEmptyRequests() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";
        BaseContextBuilder contextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of());
        AgenticToolExecutor executor = new ToolRequestExecutor(null, contextBuilder, Map.of(), sessionStore);
        executor.executeRequests(sessionId, List.of(), new CapturingListener());
        // Should return without error
    }

    @Test
    void executeHandlesToolExecutionException() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";

        String toolAssistantPayload = """
                {"toolRequests":[{"id":"call-1","name":"fail","args":{}}]}
                """;
        LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
                List.of(new Message(Role.ASSISTANT, toolAssistantPayload, null, null, null)));

        Tool failingTool = new Tool() {
            @Override
            public String name() {
                return "fail";
            }

            @Override
            public String description() {
                return "fails";
            }

            @Override
            public String execute(Map<String, Object> args) {
                throw new RuntimeException("boom");
            }
        };

        BaseContextBuilder contextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol",
                List.of(failingTool));
        AgenticToolExecutor executor = new ToolRequestExecutor(toolAssistantModel, contextBuilder,
                Map.of("fail", failingTool),
                sessionStore);
        CapturingListener listener = new CapturingListener();
        List<String> toolRequests = List.of("TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"fail\"}");

        executor.executeRequests(sessionId, toolRequests, listener);

        assertThat(listener.toolResults).hasSize(1);
        assertThat(listener.toolResults.values().iterator().next()).contains("boom");
    }

    @Test
    void executeRetriesOnMismatchWithRepairLoop() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";

        // First response: bad ID in tool call (mismatch)
        String badPayload = """
                {"toolRequests":[{"id":"WRONG-ID","name":"echo"}]}
                """;
        // Second response: correct ID (repair)
        String goodPayload = """
                {"toolRequests":[{"id":"call-1","name":"echo"}]}
                """;

        LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
                List.of(
                        new Message(Role.ASSISTANT, badPayload, null, null, null),
                        new Message(Role.ASSISTANT, goodPayload, null, null, null)));

        Tool tool = new EchoTool();
        BaseContextBuilder contextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of(tool));
        AgenticToolExecutor executor = new ToolRequestExecutor(toolAssistantModel, contextBuilder, Map.of("echo", tool),
                sessionStore);

        CapturingListener listener = new CapturingListener();
        List<String> toolRequests = List
                .of("TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\",\"args\":{\"text\":\"hi\"}}");

        executor.executeRequests(sessionId, toolRequests, listener);

        assertThat(listener.toolRepairs).isEqualTo(1);
        assertThat(listener.toolResults).hasSize(1);
    }

    @Test
    void executeReturnsEmptyListOnTotalFailure() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";

        // Repeatedly return bad payload to exhaust retries
        String badPayload = """
                {"toolRequests":[{"id":"WRONG-ID","name":"echo"}]}
                """;

        LLMModel toolAssistantModel = new QueueModel(ResponseFormatType.JSON,
                List.of(
                        new Message(Role.ASSISTANT, badPayload, null, null, null),
                        new Message(Role.ASSISTANT, badPayload, null, null, null),
                        new Message(Role.ASSISTANT, badPayload, null, null, null),
                        new Message(Role.ASSISTANT, badPayload, null, null, null),
                        new Message(Role.ASSISTANT, badPayload, null, null, null)));

        Tool tool = new EchoTool();
        BaseContextBuilder contextBuilder = new BaseContextBuilder(sessionStore, "system", "protocol", List.of(tool));
        AgenticToolExecutor executor = new ToolRequestExecutor(toolAssistantModel, contextBuilder, Map.of("echo", tool),
                sessionStore);

        CapturingListener listener = new CapturingListener();
        List<String> toolRequests = List
                .of("TOOL_REQUEST: {\"id\":\"call-1\",\"name\":\"echo\",\"args\":{\"text\":\"hi\"}}");

        List<ToolExecution> result = executor.executeRequests(sessionId, toolRequests, listener);

        assertThat(result).isEmpty();
        assertThat(listener.toolRepairs).isGreaterThan(0);
    }

    @Test
    void executeDirectlyRunsToolCalls() {
        SessionStore sessionStore = new InMemorySessionStore();
        String sessionId = "session";

        List<Tool> tools = List.of(new EchoTool());
        Map<String, Tool> toolMap = new HashMap<>();
        tools.forEach(t -> toolMap.put(t.name(), t));

        ToolExecutor executor = new ToolRequestExecutor(null, null, toolMap, sessionStore);
        CapturingListener listener = new CapturingListener();
        List<ToolCall> toolCalls = List.of(new ToolCall("call-1", "echo", Map.of("text", "hello")));

        List<ToolExecution> result = executor.execute(sessionId, toolCalls, listener);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getOutput()).isEqualTo("hello");
        assertThat(listener.toolPlans).hasSize(1);
        assertThat(listener.toolResults).hasSize(1);
    }

    private static final class QueueModel implements LLMModel {
        private final Deque<Message> responses;
        private final ResponseFormat responseFormat;

        private QueueModel(final ResponseFormatType type, final List<Message> responses) {
            this.responses = new ArrayDeque<>(responses);
            this.responseFormat = new ResponseFormat.Builder().type(type).build();
        }

        @Override
        public Message generate(final List<Message> messages) {
            if (responses.isEmpty()) {
                return new Message(Role.ASSISTANT, "", null, null, null);
            }
            return responses.removeFirst();
        }

        @Override
        public ResponseFormat responseFormat() {
            return responseFormat;
        }

        @Override
        public boolean thoughtsEnabled() {
            return true;
        }

        @Override
        public String thoughtsStartTag() {
            return "<think>";
        }

        @Override
        public String thoughtsEndTag() {
            return "</think>";
        }
    }

    private record EchoTool() implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echoes text";
        }

        @Override
        public String execute(final Map<String, Object> args) {
            return args.get("text").toString();
        }
    }

    private static final class CapturingListener implements AgentListener {
        private final List<Collection<ToolCall>> toolPlans = new ArrayList<>();
        private final Map<String, String> toolResults = new HashMap<>();
        private int toolRepairs = 0;

        @Override
        public void onToolPlan(final String sessionId, final Collection<ToolCall> toolCalls) {
            toolPlans.add(toolCalls);
        }

        @Override
        public void onToolCallResult(String sessionId, String toolCallId, String content) {
            toolResults.put(toolCallId, content);
        }

        @Override
        public void onToolRepair(final String sessionId, final List<ToolCall> toolCalls,
                final List<ToolRequest> remainingRequests) {
            toolRepairs++;
        }

        @Override
        public void onFinalAnswer(final String sessionId, final Message message) {
        }
    }
}
