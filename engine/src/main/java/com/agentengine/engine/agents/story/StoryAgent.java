package com.agentengine.engine.agents.story;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.agentengine.engine.builders.agent.AgentBuilder;
import com.agentengine.engine.agents.flows.StoryFlow;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.models.Model;

/**
 * StoryAgent is a specialized agent for methodical scene generation.
 * It uses {@link StoryFlow} to explicitly orchestrate a multistep generation process.
 */
public final class StoryAgent extends LlmAgent {

    public static final String BASE_PERSONA = 
            "You are a master storyteller and character designer. " +
            "Your task is to help generate detailed, high-quality scenes based on provided descriptions or tags. " +
            "You will be guided through a series of specific steps to build the characters, theme, and situation " +
            "before finally drafting the scene itself.";

    public StoryAgent(final AgentBuilder builder) {
        super(builder.globalInstruction(BASE_PERSONA).reWriteInstructions());
    }

    @Override
    protected BaseLlmFlow determineLlmFlow() {
        final AbstractLLM agentModel =
                (AbstractLLM) model().orElse(Model.builder().build()).model().orElse(null);
        if (agentModel == null) {
            return null;
        }
        return new StoryFlow(agentModel.getParser());
    }
}
