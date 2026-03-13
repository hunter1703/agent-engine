package com.agentengine.util.common.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutFieldRule(String effect, Map<String, Object> expr) {
}
