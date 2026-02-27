package com.agentengine.engine.builders.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.model.DelegatingLLMModel;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.agents.processors.Parser;
import com.google.adk.agents.Instruction;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultAgentBuilderTest {

  @Test
  void includesToolInstructionsWhenParsingFromText() {
    final ModelProvider modelProvider = mock(ModelProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    final ContextManagerProvider contextManagerProvider = mock(ContextManagerProvider.class);
    final ModelRepository modelRepository = mock(ModelRepository.class);

    final Parser parser = Parser.builder()
        .toolCallingEnabled(true)
        .parseToolCallsFromText(true)
        .build();
    final DelegatingLLMModel model =
        new DelegatingLLMModel(new StubLlm(), parser, "protocol", true, true);

    final BaseTool tool =
        new BaseTool("run_cmd", "run command") {
          @Override
          public Single<Map<String, Object>> runAsync(
              final Map<String, Object> args, final ToolContext toolContext) {
            return Single.just(Map.of("status", "ok"));
          }
        };

    when(modelProvider.get(any(AgentModelConfig.class))).thenReturn(model);
    when(toolRegistry.loadTools(any(), any())).thenReturn(List.of(tool));
    when(sessionServiceProvider.get(any())).thenReturn(null);
    when(modelRepository.findById(any())).thenReturn(Optional.of(new ModelConfig()));

    final DefaultAgentBuilder builder =
        new DefaultAgentBuilder(
            modelProvider,
            sessionServiceProvider,
            toolRegistry,
            contextManagerProvider,
            modelRepository);

    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model-id");
    modelConfig.setSystemPrompt("prompt");

    final AgentConfig config = new AgentConfig();
    config.setId("agent-id");
    config.setModel(modelConfig);

    final DefaultAgent agent = builder.build(config, new AgentContext(config, null));

    final Single<List<BaseTool>> toolsSingle = agent.tools();
    assertThat(toolsSingle.blockingGet()).extracting(BaseTool::name).contains("run_cmd");
    assertThat(agent.instruction()).isInstanceOf(Instruction.Static.class);
    final String instructions = ((Instruction.Static) agent.instruction()).instruction();
    assertThat(instructions).contains("run_cmd");
  }

  @Test
  void registersToolsWhenNativeToolCalling() {
    final ModelProvider modelProvider = mock(ModelProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    final ContextManagerProvider contextManagerProvider = mock(ContextManagerProvider.class);
    final ModelRepository modelRepository = mock(ModelRepository.class);

    final Parser parser = Parser.builder()
        .toolCallingEnabled(true)
        .parseToolCallsFromText(false)
        .build();
    final DelegatingLLMModel model =
        new DelegatingLLMModel(new StubLlm(), parser, "protocol", true, false);

    final BaseTool tool =
        new BaseTool("run_cmd", "run command") {
          @Override
          public Single<Map<String, Object>> runAsync(
              final Map<String, Object> args, final ToolContext toolContext) {
            return Single.just(Map.of("status", "ok"));
          }
        };

    when(modelProvider.get(any())).thenReturn(model);
    when(toolRegistry.loadTools(any(), any())).thenReturn(List.of(tool));
    when(sessionServiceProvider.get(any())).thenReturn(null);
    when(modelRepository.findById(any())).thenReturn(Optional.of(new ModelConfig()));

    final DefaultAgentBuilder builder =
        new DefaultAgentBuilder(
            modelProvider,
            sessionServiceProvider,
            toolRegistry,
            contextManagerProvider,
            modelRepository);

    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model-id");
    modelConfig.setSystemPrompt("prompt");

    final AgentConfig config = new AgentConfig();
    config.setId("agent-id");
    config.setModel(modelConfig);

    final DefaultAgent agent = builder.build(config, new AgentContext(config, null));

    final Single<List<BaseTool>> toolsSingle = agent.tools();
    assertThat(toolsSingle.blockingGet()).extracting(BaseTool::name).contains("run_cmd");
    assertThat(agent.instruction()).isInstanceOf(Instruction.Static.class);
    final String instructions = ((Instruction.Static) agent.instruction()).instruction();
    assertThat(instructions).doesNotContain("run_cmd");
  }

  @Test
  void appendsModelInstructionsToSystemPrompt() {
    final ModelProvider modelProvider = mock(ModelProvider.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    final ContextManagerProvider contextManagerProvider = mock(ContextManagerProvider.class);
    final ModelRepository modelRepository = mock(ModelRepository.class);

    final Parser parser = Parser.builder()
        .toolCallingEnabled(false)
        .parseToolCallsFromText(false)
        .build();
    final DelegatingLLMModel model =
        new DelegatingLLMModel(new StubLlm(), parser, "protocol", false, false);

    final ModelConfig modelConfig = new ModelConfig();
    modelConfig.setInstructions("Prefer concise answers.");

    when(modelProvider.get(any())).thenReturn(model);
    when(sessionServiceProvider.get(any())).thenReturn(null);
    when(modelRepository.findById(any())).thenReturn(Optional.of(modelConfig));

    final DefaultAgentBuilder builder =
        new DefaultAgentBuilder(
            modelProvider,
            sessionServiceProvider,
            toolRegistry,
            contextManagerProvider,
            modelRepository);

    final AgentModelConfig modelDefinition = new AgentModelConfig();
    modelDefinition.setModelId("model-id");
    modelDefinition.setSystemPrompt("prompt");

    final AgentConfig config = new AgentConfig();
    config.setId("agent-id");
    config.setModel(modelDefinition);

    final DefaultAgent agent = builder.build(config, new AgentContext(config, null));

    final String instructions = ((Instruction.Static) agent.instruction()).instruction();
    assertThat(instructions).contains("prompt");
    assertThat(instructions).contains("FOLLOW");
    assertThat(instructions).contains("Prefer concise answers.");
  }

  private static final class StubLlm extends BaseLlm {
    private StubLlm() {
      super("test-model");
    }

    @Override
    public Flowable<LlmResponse> generateContent(
        final LlmRequest llmRequest, final boolean stream) {
      return Flowable.empty();
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
      return null;
    }
  }
}
