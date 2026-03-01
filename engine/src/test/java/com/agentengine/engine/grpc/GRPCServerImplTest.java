package com.agentengine.engine.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.ms.MicroServiceInvocationHandler;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.server.grpc.GRPCServerImpl;
import com.agentengine.engine.utils.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.reactivex.rxjava3.core.Flowable;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GRPCServerImplTest {

  @Mock private AgentService agentService;
  @Mock private AgentExecutionService agentExecutionService;

  private Server server;
  private ManagedChannel channel;
  private AgentService agentServiceProxy;
  private AgentExecutionService agentExecutionServiceProxy;
  private ExecutorService grpcExecutor;

  @BeforeEach
  void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.setSerializationInclusion(Include.NON_NULL);

    String serverName = InProcessServerBuilder.generateName();

    // Create generic server impl with mocked services
    grpcExecutor = ThreadUtils.newVirtualThreadExecutor("grpc-test-vt-");
    GRPCServerImpl engineGRPCServer =
        new GRPCServerImpl(List.of(agentService, agentExecutionService));

    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(engineGRPCServer)
            .build()
            .start();

    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

    // Create generic client proxies
    agentServiceProxy =
        (AgentService)
            Proxy.newProxyInstance(
                AgentService.class.getClassLoader(),
                new Class[] {AgentService.class},
                new MicroServiceInvocationHandler(AgentService.class, channel));

    agentExecutionServiceProxy =
        (AgentExecutionService)
            Proxy.newProxyInstance(
                AgentExecutionService.class.getClassLoader(),
                new Class[] {AgentExecutionService.class},
                new MicroServiceInvocationHandler(AgentExecutionService.class, channel));
  }

  @AfterEach
  void tearDown() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
    if (grpcExecutor != null) {
      grpcExecutor.shutdownNow();
    }
  }

  @Test
  void testGetAgent() {
    String agentId = UUID.randomUUID().toString();
    AgentConfig config = new AgentConfig();
    config.setId(agentId);
    config.setName("Test Agent");
    config.setDescription("Test Description");
    AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("gpt-4");
    config.setModel(modelConfig);

    when(agentService.getAgent(agentId)).thenReturn(Optional.of(config));

    // Call via generic proxy
    Optional<AgentConfig> resultOpt = agentServiceProxy.getAgent(agentId);

    assertThat(resultOpt).isPresent();
    AgentConfig result = resultOpt.get();
    assertThat(result.getId()).isEqualTo(agentId);
    assertThat(result.getName()).isEqualTo("Test Agent");
    assertThat(result.getDescription()).isEqualTo("Test Description");
    assertThat(result.getModel().getModelId()).isEqualTo("gpt-4");
  }

  @Test
  void testGetAgentNotFound() {
    when(agentService.getAgent("missing")).thenReturn(Optional.empty());

    Optional<AgentConfig> resultOpt = agentServiceProxy.getAgent("missing");

    assertThat(resultOpt).isEmpty();
  }

  @Test
  void testDeleteAgent() {
    when(agentService.deleteAgent("test-id")).thenReturn(true);

    boolean deleted = agentServiceProxy.deleteAgent("test-id");

    assertThat(deleted).isTrue();
  }

  @Test
  void testFindAgents() {
    AgentConfig config = new AgentConfig();
    config.setId("agent-1");
    PaginatedResult<AgentConfig> mockResult = new PaginatedResult<>();
    mockResult.setItems(List.of(config));
    mockResult.setTotal(1L);

    when(agentService.findAgents(any())).thenReturn(mockResult);

    PaginatedResult<AgentConfig> result = agentServiceProxy.findAgents(new Query());

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getId()).isEqualTo("agent-1");
    assertThat(result.getTotal()).isEqualTo(1);
  }

  @Test
  void testRun() {
    String agentId = "test-agent";
    String sessionId = "test-session";
    Event event =
        Event.builder()
            .id("event-1")
            .author("model")
            .content(Content.fromParts(Part.fromText("response")))
            .build();

    AgentRequest request = new AgentRequest();
    request.setAgentId(agentId);
    request.setSessionId(sessionId);

    when(agentExecutionService.run(any(AgentRequest.class))).thenReturn(Flowable.just(event));

    // Call via generic proxy
    Flowable<Event> resultFlow = agentExecutionServiceProxy.run(request);

    List<Event> results = resultFlow.toList().blockingGet();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).id()).isEqualTo("event-1");
    assertThat(results.get(0).author()).isEqualTo("model");
  }

  @Test
  void testServerErrorPropagation() {
    when(agentService.getAgent("error")).thenThrow(new RuntimeException("Simulated server error"));

    assertThatThrownBy(() -> agentServiceProxy.getAgent("error"))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("INTERNAL");
  }
}
