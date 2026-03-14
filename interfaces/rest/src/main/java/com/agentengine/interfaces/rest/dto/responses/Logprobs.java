package com.agentengine.interfaces.rest.dto.responses;

import java.util.List;

/**
 * Log probability information.
 */
public record Logprobs(List<TokenLogprob> content, List<TokenLogprob> refusal) {
}
