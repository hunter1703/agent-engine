package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Output content item.
 */
public record OutputContent(String type, String text, List<Annotation> annotations, @JsonProperty("refusal") String refusal) {
}
