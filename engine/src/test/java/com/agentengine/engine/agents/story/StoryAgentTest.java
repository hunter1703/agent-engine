package com.agentengine.engine.agents.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.builders.agent.LLMAgentBuilder;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.Instruction;
import org.junit.jupiter.api.Test;

class StoryAgentTest {

    @Test
    void storyAgentIsCorrectlyConfigured() {
        final AbstractLLM mockLlm = mock(AbstractLLM.class);
        final Parser mockParser = mock(Parser.class);
        when(mockLlm.getParser()).thenReturn(mockParser);

        final LLMAgentBuilder builder = new LLMAgentBuilder();
        builder.name("StoryGen");
        builder.model(mockLlm);
        builder.globalInstruction("Custom global info");
        builder.protocolInstructions("Standard protocol");

        final StoryAgent storyAgent = new StoryAgent(builder, mockLlm, 0);

        final String instructions = storyAgent.instruction() instanceof Instruction.Static staticInstr
                ? staticInstr.instruction()
                : "";
        assertThat(instructions).contains(StoryAgent.BASE_PERSONA);
    }
}
