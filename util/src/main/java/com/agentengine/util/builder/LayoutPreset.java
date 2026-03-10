package com.agentengine.util.builder;

import java.util.Map;

public record LayoutPreset(
    String id,
    String label,
    String description,
    Boolean isDefault,
    Map<String, Object> preset) {}
