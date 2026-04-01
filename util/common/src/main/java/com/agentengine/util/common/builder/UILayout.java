package com.agentengine.util.common.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UILayout(Map<String, LayoutField> fields, List<LayoutPreset> presets) {}
