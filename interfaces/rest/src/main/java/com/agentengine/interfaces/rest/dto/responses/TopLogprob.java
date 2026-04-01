package com.agentengine.interfaces.rest.dto.responses;

import java.util.List;

/** Top log probability for a token. */
public record TopLogprob(String token, double logprob, List<Integer> bytes) {}
