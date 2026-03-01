package com.agentengine.engine.builders.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.story.StoryAgent;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class StoryAgentExposureTest {

  @Test
  @SuppressWarnings("unchecked")
  public void testStoryAgentIsResolvedByType() {
    // Mock the builders
    final StoryAgentBuilder storyBuilder = mock(StoryAgentBuilder.class);
    when(storyBuilder.type()).thenReturn("story");
    
    final DefaultAgentBuilder defaultBuilder = mock(DefaultAgentBuilder.class);
    when(defaultBuilder.type()).thenReturn("default");

    // Mock the Instance for AgentProvider
    Instance<AgentBuilder<?, ?>> allBuilders = mock(Instance.class);
    when(allBuilders.stream()).thenReturn(Stream.of(storyBuilder, defaultBuilder));

    final AgentProvider agentProvider = new AgentProvider(allBuilders);

    // Test resolution
    final AgentConfig config = new AgentConfig(AgentConfig.AgentType.STORY);
    final AgentContext context = mock(AgentContext.class);
    final StoryAgent mockAgent = mock(StoryAgent.class);
    
    when(storyBuilder.build(config, context)).thenReturn(mockAgent);

    final StoryAgent resolved = agentProvider.get(config, context);
    assertNotNull(resolved);
  }
}
