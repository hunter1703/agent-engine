package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.AbstractTool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Single;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbstractSimpleToolProviderTestDefinition {

  @Test
  void exposesDescriptorsAndCreatesTools() {
    AbstractTool tool = new SampleAbstractTool();
    Instance<AbstractTool> tools = mock(Instance.class);
    Instance<AbstractTool> selected = mock(Instance.class);
    when(tools.iterator()).thenReturn(List.of(tool).iterator());
    when(tools.select(tool.getClass())).thenReturn(selected);
    when(selected.get()).thenReturn(tool);

    SimpleToolProvider provider = new SimpleToolProvider(tools);

    List<ToolDescriptor> descriptors = provider.tools();
    assertThat(descriptors).hasSize(1);
    ToolDescriptor descriptor = descriptors.get(0);
    assertThat(descriptor.name()).isEqualTo("simple-tool");
    assertThat(descriptor.agentId()).isEqualTo("test-agent");
    assertThat(descriptor.subTool()).isTrue();

    BaseTool created = provider.create(null, "simple-tool", Map.of("prefix", "pre-"));
    assertThat(created).isNotNull();
    Map<String, Object> output =
        created.runAsync(Map.of("value", "fix"), null).blockingGet();
    assertThat(output).containsEntry("output", "pre-fix");
    assertThat(provider.create(null, "missing", Map.of())).isNull();
  }

  private static final class SampleAbstractTool implements AbstractTool {
    @Override
    public String name() {
      return "simple-tool";
    }

    @Override
    public String agentId() {
      return "test-agent";
    }

    @Override
    public boolean subTool() {
      return true;
    }

    @Override
    public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
      final String prefix =
          toolConfig == null ? "" : toolConfig.getOrDefault("prefix", "").toString();
      return new BaseTool("simple-tool", "sample tool") {
        @Override
        public Single<Map<String, Object>> runAsync(
            final Map<String, Object> args, final ToolContext toolContext) {
          final String value = args.getOrDefault("value", "").toString();
          return Single.just(Map.of("output", prefix + value));
        }
      };
    }
  }
}
