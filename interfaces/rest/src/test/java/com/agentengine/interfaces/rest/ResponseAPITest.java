package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.interfaces.rest.dto.responses.ChatCompletionsRequest;
import com.agentengine.interfaces.rest.dto.responses.ChatCompletionResponse;
import com.agentengine.interfaces.rest.dto.responses.EmbeddingsRequest;
import com.agentengine.interfaces.rest.dto.responses.ErrorResponse;
import com.agentengine.interfaces.rest.dto.responses.ResponsesObject;
import com.agentengine.interfaces.rest.dto.responses.ResponsesRequest;
import com.agentengine.util.common.query.PaginatedResult;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResponseAPITest {

  @Test
  void shouldExposeAgentsAsOpenAiModelsSortedById() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);

    final DefaultAgentConfig second = new DefaultAgentConfig();
    second.setId("z_agent");
    second.setName("Z Agent");
    second.setCreatedTime(1_000L);

    final DefaultAgentConfig first = new DefaultAgentConfig();
    first.setId("a_agent");
    first.setName("A Agent");
    first.setCreatedTime(2_000L);

    when(agentService.findAgents(any())).thenReturn(PaginatedResult.create(List.of(second, first)));
    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");

    final ModelListResponse response = api.models();

    assertThat(response.object()).isEqualTo("list");
    assertThat(response.data()).extracting(item -> item.id()).containsExactly("a_agent", "z_agent");
    assertThat(response.data()).extracting(item -> item.object()).containsOnly("model");
  }

  @Test
  void shouldUseChatHeaderAsSessionSourceOverUser() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn("chat-42");

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    final ChatCompletionsRequest payloadFirst = chatPayload("echo_agent", "hello 1", false, "user-a");
    final ChatCompletionsRequest payloadSecond = chatPayload("echo_agent", "hello 2", false, "user-b");

    api.chatCompletions(payloadFirst, headers);
    api.chatCompletions(payloadSecond, headers);

    final ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
    verify(executionService, times(2)).run(eq("echo_agent"), sessionCaptor.capture(), any());
    final List<String> captured = sessionCaptor.getAllValues();
    assertThat(captured).hasSize(2);
    assertThat(captured.getFirst()).isEqualTo(captured.getLast()).startsWith("owui-");
  }

  @Test
  void shouldNamespaceSessionByAgentAndChatId() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("agent_a")).thenReturn(Optional.of(agent("agent_a")));
    when(agentService.getAgent("agent_b")).thenReturn(Optional.of(agent("agent_b")));
    when(executionService.run(eq("agent_a"), any(), any())).thenReturn(sampleRun());
    when(executionService.run(eq("agent_b"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn("shared-chat");

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    api.chatCompletions(chatPayload("agent_a", "hello a", false, "user"), headers);
    api.chatCompletions(chatPayload("agent_b", "hello b", false, "user"), headers);

    final ArgumentCaptor<String> agentASession = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> agentBSession = ArgumentCaptor.forClass(String.class);
    verify(executionService).run(eq("agent_a"), agentASession.capture(), any());
    verify(executionService).run(eq("agent_b"), agentBSession.capture(), any());
    assertThat(agentASession.getValue()).isNotEqualTo(agentBSession.getValue());
  }

  @Test
  void shouldFallbackToUserSessionWhenHeaderMissing() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn(null);

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    api.chatCompletions(chatPayload("echo_agent", "hello", false, "user-1"), headers);
    api.chatCompletions(chatPayload("echo_agent", "hello again", false, "user-1"), headers);

    final ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
    verify(executionService, times(2)).run(eq("echo_agent"), sessionCaptor.capture(), any());
    assertThat(sessionCaptor.getAllValues().getFirst()).isEqualTo(sessionCaptor.getAllValues().getLast()).startsWith("owui-");
  }

  @Test
  void shouldFallbackToRandomSessionWhenNoHeaderOrUser() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn(null);

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    final ChatCompletionsRequest payload = chatPayload("echo_agent", "hello", false, null);
    api.chatCompletions(payload, headers);
    api.chatCompletions(payload, headers);

    final ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
    verify(executionService, times(2)).run(eq("echo_agent"), sessionCaptor.capture(), any());
    assertThat(sessionCaptor.getAllValues().getFirst()).isNotEqualTo(sessionCaptor.getAllValues().getLast());
  }

  @Test
  void shouldReturnStreamingResponsesEvents() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn("chat-1");

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    final ResponsesRequest payload = responsesPayload("echo_agent", true);

    final Response response = api.responses(payload, headers);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(String.valueOf(response.getMediaType())).contains("text/event-stream");

    final List<String> chunks = toStringChunks(response.getEntity());
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.stream().anyMatch(chunk -> chunk.contains("\"type\":\"response.completed\""))).isTrue();
  }

  @Test
  void shouldReturnNonStreamingResponsesPayloadWithDoneFlag() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn("chat-1");

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    final Response response = api.responses(responsesPayload("echo_agent", false), headers);
    assertThat(response.getStatus()).isEqualTo(200);

    assertThat(response.getEntity()).isInstanceOf(ResponsesObject.class);
    final ResponsesObject entity = (ResponsesObject) response.getEntity();
    assertThat(entity.done()).isTrue();
    assertThat(entity.output()).isNotNull();
  }

  @Test
  void shouldReturnChatCompletionStreamAndDoneMarker() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn("chat-1");

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    final Response response = api.chatCompletions(chatPayload("echo_agent", "tell me something", true, "user-x"), headers);

    assertThat(response.getStatus()).isEqualTo(200);
    final List<String> chunks = toStringChunks(response.getEntity());
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.getLast()).isEqualTo("[DONE]");
    assertThat(chunks.stream().anyMatch(chunk -> chunk.contains("\"chat.completion.chunk\""))).isTrue();
  }

  @Test
  void shouldReturnNonStreamingChatCompletionWithOutputItems() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    when(agentService.getAgent("echo_agent")).thenReturn(Optional.of(agent("echo_agent")));
    when(executionService.run(eq("echo_agent"), any(), any())).thenReturn(sampleRun());

    final HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.getHeaderString("X-OpenWebUI-Chat-Id")).thenReturn("chat-2");

    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");
    final Response response = api.chatCompletions(chatPayload("echo_agent", "tell me something", false, "user-x"), headers);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(ChatCompletionResponse.class);
    final ChatCompletionResponse entity = (ChatCompletionResponse) response.getEntity();
    assertThat(entity.object()).isEqualTo("chat.completion");
    assertThat(entity.output()).isNotNull();
  }

  @Test
  void shouldReturnNotImplementedForEmbeddings() {
    final AgentService agentService = org.mockito.Mockito.mock(AgentService.class);
    final AgentExecutionService executionService = org.mockito.Mockito.mock(AgentExecutionService.class);
    final ResponseAPI api = new ResponseAPI(agentService, executionService, "X-OpenWebUI-Chat-Id");

    final EmbeddingsRequest request = new EmbeddingsRequest();
    request.setModel("echo_agent");
    request.setInput("text");
    final Response response = api.embeddings(request);

    assertThat(response.getStatus()).isEqualTo(501);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    final ErrorResponse entity = (ErrorResponse) response.getEntity();
    assertThat(entity.error()).isNotNull();
  }

  private static DefaultAgentConfig agent(final String id) {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId(id);
    config.setName(id);
    return config;
  }

  private static Flowable<Event> sampleRun() {
    final Event first = Event.builder().id("evt-1").partial(true)
        .content(Content.builder().role("model").parts(List.of(Part.builder().thought(true).text("thinking ").build())).build()).build();
    final Event second = Event.builder().id("evt-2").turnComplete(true).finishReason(new FinishReason(FinishReason.Known.STOP))
        .content(Content.builder().role("model").parts(List.of(Part.fromText("final answer"))).build()).build();
    return Flowable.just(first, second);
  }

  private static ChatCompletionsRequest chatPayload(final String model, final String message, final boolean stream, final String user) {
    final ChatCompletionsRequest payload = new ChatCompletionsRequest();
    payload.setModel(model);
    payload.setStream(stream);
    payload.setMessages(List.of(Map.of("role", "user", "content", message)));
    payload.setUser(user);
    return payload;
  }

  private static ResponsesRequest responsesPayload(final String model, final boolean stream) {
    final ResponsesRequest payload = new ResponsesRequest();
    payload.setModel(model);
    payload.setStream(stream);
    payload.setInput(List.of(Map.of("type", "message", "role", "user", "content", List.of(Map.of("type", "input_text", "text", "hi")))));
    return payload;
  }

  private static List<String> toStringChunks(final Object entity) {
    assertThat(entity).isInstanceOf(Flowable.class);
    final Flowable<?> flowable = (Flowable<?>) entity;
    return flowable.map(String::valueOf).toList().blockingGet();
  }
}
