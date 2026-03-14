package com.agentengine.interfaces.rest.dto.responses;

/**
 * A tool definition.
 */
public record Tool(
    String type,
    FunctionDefinition function
) {
}
