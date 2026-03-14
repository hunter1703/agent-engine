package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Text output format configuration.
 */
public record TextConfig(ResponseFormat format) {
}
