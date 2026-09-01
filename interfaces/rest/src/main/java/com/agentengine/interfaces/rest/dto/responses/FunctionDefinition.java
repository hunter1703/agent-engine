package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.databind.JsonNode;

public record FunctionDefinition(String name, String description, JsonNode parameters) {}
