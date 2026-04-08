package com.agentengine.util.common.builder;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutSection(String id, String label, String description, Integer order) {}
