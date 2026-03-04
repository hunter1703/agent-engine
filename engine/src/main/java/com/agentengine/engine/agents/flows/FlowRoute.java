package com.agentengine.engine.agents.flows;

import com.google.adk.flows.llmflows.BaseLlmFlow;

/**
 * Describes a routing option: a named flow with a description used for LLM-based classification.
 *
 * @param name        unique identifier matched against the routing model's response (e.g. {@code "STORY_GENERATION"})
 * @param description sentence fed to the routing LLM to explain when this route applies
 * @param flow        the flow to invoke when this route is selected
 */
public record FlowRoute(String name, String description, BaseLlmFlow flow) {

    public static FlowRoute of(final String name, final String description, final BaseLlmFlow flow) {
        return new FlowRoute(name, description, flow);
    }
}
