package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Token log probability. */
public record TokenLogprob(
    String token,
    double logprob,
    List<Integer> bytes,
    @JsonProperty("top_logprobs") List<TopLogprob> topLogprobs) {}
