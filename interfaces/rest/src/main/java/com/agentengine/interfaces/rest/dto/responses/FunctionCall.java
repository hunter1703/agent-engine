package com.agentengine.interfaces.rest.dto.responses;

/**
 * A function call within a tool call.
 */
public record FunctionCall(String name, String arguments) {
}
