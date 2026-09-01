package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AudioConfig(
    String voice,
    @JsonProperty("response_format") String responseFormat,
    @JsonProperty("end_on_sentence") Boolean endOnSentence) {}
