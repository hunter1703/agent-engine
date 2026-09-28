package com.agentengine.util.agents.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutLookup(Boolean multiSelect, String assetType, Map<String, String> options) {}
