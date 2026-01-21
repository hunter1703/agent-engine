package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.commons.utils.JsonUtils;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.api.state.SessionStore;
import com.agentengine.engine.api.beans.session.ToolRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineUtilsTest {

  @Test
  void invocationsThisTurnCountsAssistantMessagesSinceLastUser() {
    SessionStore sessionStore = new InMemorySessionStore();
    String sessionId = "session";

    sessionStore.appendMessage(sessionId, Message.user("hello"));
    sessionStore.appendMessage(sessionId, Message.assistant("first"));
    sessionStore.appendMessage(sessionId, Message.assistant("second"));
    sessionStore.appendMessage(sessionId, Message.user("reset"));
    sessionStore.appendMessage(sessionId, Message.assistant("third"));

    assertThat(EngineUtils.invocationsThisTurn(sessionStore, sessionId)).isEqualTo(1);
  }

  @Test
  void sanitizeMessageReturnsEmptyPayloadForInvalidJsonResponse() {
    Message response = new Message(Role.ASSISTANT, "not-json", null, null, null);
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.JSON).build();

    Message sanitized = EngineUtils.sanitizeMessage(response, format, false, "<think>", "</think>");

    assertThat(sanitized.getContent()).isEmpty();
    assertThat(sanitized.getToolRequests()).isEmpty();
    assertThat(sanitized.getToolCalls()).isEmpty();
  }

  @Test
  void sanitizeMessageReturnsNullForNullInput() {
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();

    Message sanitized = EngineUtils.sanitizeMessage(null, format, false, "<think>", "</think>");

    assertThat(sanitized).isNull();
  }

  @Test
  void sanitizeMessageParsesJsonPayload() {
    String payload = "{\"finalAnswer\":\"ok\",\"toolRequests\":[{\"id\":\"1\",\"name\":\"echo\"}]}";
    Message response = new Message(Role.ASSISTANT, payload, null, null, null);
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.JSON).build();

    Message sanitized = EngineUtils.sanitizeMessage(response, format, false, "<think>", "</think>");

    assertThat(sanitized.getContent()).isEqualTo("ok");
    assertThat(sanitized.getToolRequests()).hasSize(1);
  }

  @Test
  void sanitizeMessageExtractsThoughtsAndToolRequestsFromText() {
    String content = "<think>plan</think>\nTOOL_REQUEST: {\"id\":\"1\",\"name\":\"echo\"}";
    Message response = new Message(Role.ASSISTANT, content, null, null, null);
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();

    Message sanitized = EngineUtils.sanitizeMessage(response, format, true, "<think>", "</think>");

    assertThat(sanitized.getThoughts()).isEqualTo("plan");
    assertThat(sanitized.getToolRequests()).containsExactly("{\"id\":\"1\",\"name\":\"echo\"}");
    assertThat(sanitized.getContent()).isNull();
  }

  @Test
  void sanitizeMessageExtractsFinalAnswerFromText() {
    Message response = new Message(Role.ASSISTANT, "FINAL: done", null, null, null);
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();

    Message sanitized = EngineUtils.sanitizeMessage(response, format, false, "<think>", "</think>");

    assertThat(sanitized.getContent()).isEqualTo("done");
    assertThat(sanitized.getToolRequests()).isEmpty();
  }

  @Test
  void sanitizeMessageKeepsContentWhenThoughtTagsMissing() {
    Message response = new Message(Role.ASSISTANT, "plain", null, null, null);
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();

    Message sanitized = EngineUtils.sanitizeMessage(response, format, true, "", "");

    assertThat(sanitized.getContent()).isEqualTo("plain");
    assertThat(sanitized.getThoughts()).isNull();
  }

  @Test
  void parseJsonPayloadHandlesCodeFencesAndToolCalls() {
    String payload = """
        ```json
        {"finalAnswer":"ok","thoughts":"t","toolRequests":[{"id":"t1","name":"echo","args":{"text":"hi"}}]}
        ```
        """;

    Message parsed = EngineUtils.parseJsonPayload(payload);

    assertThat(parsed.getContent()).isEqualTo("ok");
    assertThat(parsed.getThoughts()).isEqualTo("t");
    assertThat(parsed.getToolCalls()).hasSize(1);
    assertThat(parsed.getToolCalls().getFirst().name()).isEqualTo("echo");
    assertThat(parsed.getToolRequests()).hasSize(1);
  }

  @Test
  void parseJsonPayloadFallsBackToToolNameAndArgs() {
    String payload = "{\"tool_name\":\"echo\",\"tool_args\":{\"text\":\"hi\"}}";

    Message parsed = EngineUtils.parseJsonPayload(payload);

    assertThat(parsed.getToolCalls()).hasSize(1);
    assertThat(parsed.getToolCalls().getFirst().name()).isEqualTo("echo");
    assertThat(parsed.getToolRequests()).hasSize(1);
    assertThat(parsed.getToolRequests().getFirst()).contains("echo");
  }

  @Test
  void parseJsonPayloadReturnsNullForInvalidJson() {
    Message parsed = EngineUtils.parseJsonPayload("not-json");

    assertThat(parsed).isNull();
  }

  @Test
  void parseJsonPayloadExtractsEmbeddedJson() {
    Message parsed = EngineUtils.parseJsonPayload("prefix {\"finalAnswer\":\"ok\"} suffix");

    assertThat(parsed.getContent()).isEqualTo("ok");
  }

  @Test
  void parseJsonPayloadHandlesMixedToolRequestFormats() {
    String payload = JsonUtils.toJson(Map.of("toolRequests", List.of("raw", Map.of("id", "2", "name", "tool2"), 3)));

    Message parsed = EngineUtils.parseJsonPayload(payload);

    assertThat(parsed).isNotNull();
    assertThat(parsed.getToolRequests()).hasSize(3);
    assertThat(parsed.getToolRequests().getFirst()).isEqualTo("raw");
  }

  @Test
  void parseToolRequestInfoParsesBulletListKeys() {
    List<ToolRequest> parsed = EngineUtils.parseToolRequestInfo(List.of("- id: 9\n- tool: ping"));

    assertThat(parsed.getFirst().id()).isEqualTo("9");
    assertThat(parsed.getFirst().name()).isEqualTo("ping");
  }

  @Test
  void parseToolRequestInfoExtractsIdAndNameFromJsonOrLines() {
    List<ToolRequest> parsed = EngineUtils
        .parseToolRequestInfo(List.of("{\"id\":\"abc\",\"name\":\"tool\"}", "id: 2\nname: calc"));

    assertThat(parsed).hasSize(2);
    assertThat(parsed.getFirst().id()).isEqualTo("abc");
    assertThat(parsed.get(1).name()).isEqualTo("calc");
  }

  @Test
  void parseToolRequestInfoExtractsEmbeddedJson() {
    List<ToolRequest> parsed = EngineUtils
        .parseToolRequestInfo(List.of("prefix {\"id\":\"t1\",\"tool\":\"sum\"} suffix"));

    assertThat(parsed.getFirst().id()).isEqualTo("t1");
    assertThat(parsed.getFirst().name()).isEqualTo("sum");
  }

  @Test
  void sanitizeMessageExtractsMultipleToolRequests() {
    String content = "TOOL_REQUEST: {\"id\":\"1\",\"name\":\"a\"}\nTOOL_REQUEST: {\"id\":\"2\",\"name\":\"b\"}";
    Message response = new Message(Role.ASSISTANT, content, null, null, null);
    ResponseFormat format = new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();

    Message sanitized = EngineUtils.sanitizeMessage(response, format, false, "<think>", "</think>");

    assertThat(sanitized.getToolRequests()).hasSize(2);
  }

  @Test
  void parseJsonPayloadHandlesToolNameWithoutArgs() {
    Message parsed = EngineUtils.parseJsonPayload("{\"tool_name\":\"echo\"}");

    assertThat(parsed.getToolCalls()).hasSize(1);
    assertThat(parsed.getToolCalls().getFirst().name()).isEqualTo("echo");
  }

  @Test
  void getRepairMessageIfInvalidFlagsMixedFinalAndToolRequests() {
    Message message = new Message(Role.ASSISTANT, "answer", null, List.of("{\"name\":\"tool\"}"), List.of());

    String repairMessage = EngineUtils.getRepairMessageIfInvalid(message);

    assertThat(repairMessage).contains("You cannot give both final answer and tool requests");
    assertThat(repairMessage).contains("Each tool request must include an \"id\"");
  }

  @Test
  void getRepairMessageIfInvalidFlagsDuplicateIdsAndMissingName() {
    Message message = new Message(Role.ASSISTANT, null, null,
        List.of("{\"id\":\"1\"}", "{\"id\":\"1\",\"name\":\"tool\"}"), List.of());

    String repairMessage = EngineUtils.getRepairMessageIfInvalid(message);

    assertThat(repairMessage).contains("Each tool request must include a tool name");
    assertThat(repairMessage).contains("Tool request ids must be unique");
  }

  @Test
  void getRepairMessageIfInvalidFlagsEmptyResponse() {
    Message message = new Message(Role.ASSISTANT, null, null, List.of(), List.of());

    String repairMessage = EngineUtils.getRepairMessageIfInvalid(message);

    assertThat(repairMessage).contains("You must provide a final answer or tool requests");
  }

  @Test
  void getRepairMessageIfInvalidReturnsEmptyForValidResponse() {
    Message message = new Message(Role.ASSISTANT, "ok", null, List.of(), List.of());

    String repairMessage = EngineUtils.getRepairMessageIfInvalid(message);

    assertThat(repairMessage).isEmpty();
  }

  @Test
  void parseToolRequestInfoHandlesEmptyList() {
    List<ToolRequest> parsed = EngineUtils.parseToolRequestInfo(List.of());

    assertThat(parsed).isEmpty();
  }
}
