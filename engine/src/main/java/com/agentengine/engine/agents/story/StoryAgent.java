package com.agentengine.engine.agents.story;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.agentengine.engine.agents.flows.FlowRoute;
import com.agentengine.engine.agents.flows.RoutingFlow;
import com.agentengine.engine.agents.flows.StoryFlow;
import com.agentengine.engine.builders.agent.AgentBuilder;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Model;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * StoryAgent is a specialized agent for methodical scene generation.
 *
 * <p>Uses {@link RoutingFlow} to classify each invocation and dispatch to either
 * {@link StoryFlow} (full 6-phase generation) or {@link DefaultFlow} (conversational follow-up).
 * The routing decision is made per-invocation via a lightweight LLM call that does not write
 * to the session.
 */
public final class StoryAgent extends LlmAgent {

    public static final String BASE_PERSONA =
            "You are a master storyteller and character designer. " +
            "Your task is to help generate detailed, high-quality scenes based on provided descriptions or tags. " +
            "You will be guided through a series of specific steps to build the characters, theme, and situation " +
            "before finally drafting the scene itself.";

    private final RoutingFlow routingFlow;

    public StoryAgent(final AgentBuilder builder, final AbstractLLM routingModel, final int routingHistorySize) {
        super(builder.globalInstruction(BASE_PERSONA).reWriteInstructions());
        final AbstractLLM agentModel = (AbstractLLM) model()
                .orElse(Model.builder().build())
                .model()
                .orElse(null);
        if (agentModel == null) {
            throw new IllegalStateException("StoryAgent requires a configured AbstractLLM model");
        }
        this.routingFlow = new RoutingFlow(
                routingModel,
                List.of(
                        FlowRoute.of(
                                "STORY_GENERATION",
                                "User is explicitly requesting a brand-new story or scene to be generated from scratch (e.g. 'write a story about X', 'create a scene where Y'). Do NOT select this for questions about an existing story, requests to modify a story, word count questions, character questions, or any follow-up.",
                                new StoryFlow(agentModel.getParser())),
                        FlowRoute.of(
                                "CONVERSATION",
                                "User asks any question or makes any request that is not generating a new story from scratch — including questions about characters, word counts, story details, greetings, clarifications, requests to modify an existing story, or general conversation.",
                                new DefaultFlow(agentModel.getParser()))
                ),
                routingHistorySize
        );
    }

    @Override
    protected Flowable<Event> runAsyncImpl(final InvocationContext context) {
        return routingFlow.run(context);
    }

    // determineLlmFlow() is not overridden — the ADK's default llmFlow is stored but never
    // called because runAsyncImpl() is overridden. This is intentional.
}
