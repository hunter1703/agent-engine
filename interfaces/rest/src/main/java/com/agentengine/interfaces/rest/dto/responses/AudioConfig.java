package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Audio configuration for voice models.
 */
public record AudioConfig(String voice, @JsonProperty("response_format") String responseFormat,
    @JsonProperty("end_on_sentence") Boolean endOnSentence) {
}
