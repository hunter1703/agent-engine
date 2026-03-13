package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.plugin.tools.ToolProvider;
import com.agentengine.engine.plugin.tools.ToolsetProvider;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolServiceImplTest {

  @Test
  void shouldExposeRegisteredToolsWithoutAgentScopedFiltering() {
    final ToolDescriptor descriptor = new ToolDescriptor("shared_tool", "Shared tool", Map.of());
    final ToolProvider provider = new StaticToolProvider(descriptor);
    final ToolServiceImpl catalog = new ToolServiceImpl(instanceOf(List.of(provider)), instanceOf(List.<ToolsetProvider>of()),
        emptyDiscoveredToolProviders());

    assertThat(catalog.getTools()).containsExactly(descriptor);
    assertThat(catalog.getToolByName("shared_tool")).isSameAs(descriptor);
  }

  @Test
  void shouldHideToolsetMembersFromTopLevelVisibleList() {
    final ToolDescriptor toolDescriptor = new ToolDescriptor("member_tool", "Member tool", Map.of());
    final ToolDescriptor toolsetDescriptor = new ToolDescriptor("planning", "Planning toolset", Map.of());
    final ToolProvider provider = new StaticToolProvider(toolDescriptor);
    final ToolsetProvider toolsetProvider = new ToolsetProvider() {
      @Override
      public ToolDescriptor descriptor() {
        return toolsetDescriptor;
      }

      @Override
      public List<ToolDescriptor> memberDescriptors() {
        return List.of(toolDescriptor);
      }

      @Override
      public com.google.adk.tools.BaseToolset create(final Map<String, Object> toolConfig) {
        return null;
      }
    };
    final ToolServiceImpl catalog = new ToolServiceImpl(instanceOf(List.of(provider)), instanceOf(List.of(toolsetProvider)),
        emptyDiscoveredToolProviders());

    assertThat(catalog.getTools()).containsExactly(toolsetDescriptor);
    assertThat(catalog.getToolByName("planning")).isSameAs(toolsetDescriptor);
    assertThat(catalog.getToolByName("member_tool")).isNull();
  }

  @SuppressWarnings("unchecked")
  private static <T> Instance<T> instanceOf(final List<T> values) {
    final Instance<T> instance = mock(Instance.class);
    when(instance.iterator()).thenReturn(values.iterator());
    return instance;
  }

  @SuppressWarnings("unchecked")
  private static DiscoveredToolProviders emptyDiscoveredToolProviders() {
    final Instance<Tool> tools = mock(Instance.class);
    when(tools.iterator()).thenReturn(List.<Tool>of().iterator());
    return new DiscoveredToolProviders(tools);
  }

  private static final class StaticToolProvider implements ToolProvider {
    private final ToolDescriptor descriptor;

    private StaticToolProvider(final ToolDescriptor descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public ToolDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public com.google.adk.tools.BaseTool create(final Map<String, Object> toolConfig) {
      return null;
    }
  }
}
