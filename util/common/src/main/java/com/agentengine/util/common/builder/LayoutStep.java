package com.agentengine.util.common.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutStep(
    String id, String label, String description, Integer order, List<LayoutSection> sections) {}
