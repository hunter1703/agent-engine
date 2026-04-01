package com.agentengine.interfaces.rest.dto.responses;

/** Annotation for output content. */
public record Annotation(String type, String url, int startIndex, int endIndex) {}
