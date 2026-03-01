package com.agentengine.engine.agents.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.builders.agent.AgentBuilder;
import com.agentengine.engine.agents.flows.StoryFlow;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.flows.BaseFlow;
import org.junit.jupiter.api.Test;

class StoryAgentTest {

    @Test
    void storyAgentIsCorrectlyConfigured() {
        // Mock the LLM and its Parser to avoid NPE in determineLlmFlow
        final AbstractLLM mockLlm = mock(AbstractLLM.class);
        final Parser mockParser = mock(Parser.class);
        when(mockLlm.getParser()).thenReturn(mockParser);

        final AgentBuilder builder = new AgentBuilder();
        builder.name("StoryGen");
        builder.model(mockLlm);
        builder.globalInstruction("Custom global info");
        builder.protocolInstructions("Standard protocol");
        
        final StoryAgent storyAgent = new StoryAgent(builder);

        // Verify Base Persona
        final String instructions = storyAgent.instruction() instanceof com.google.adk.agents.Instruction.Static staticInstr 
                ? staticInstr.instruction() 
                : "";
        assertThat(instructions).contains(StoryAgent.BASE_PERSONA);

        // Verify Flow Type
        BaseFlow flow = storyAgent.determineLlmFlow();
        assertThat(flow).isInstanceOf(StoryFlow.class);
    }
}
