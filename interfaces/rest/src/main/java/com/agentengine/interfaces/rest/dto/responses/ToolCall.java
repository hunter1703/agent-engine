package com.agentengine.interfaces.rest.dto.responses;

public record ToolCall(String id, String type, FunctionCall function, Integer index) {}
