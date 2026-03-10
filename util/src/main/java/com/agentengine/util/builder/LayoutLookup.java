package com.agentengine.util.builder;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutLookup(
    Boolean multiSelect,
    String assetType) {}
