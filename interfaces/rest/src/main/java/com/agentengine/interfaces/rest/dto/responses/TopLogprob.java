package com.agentengine.interfaces.rest.dto.responses;

import java.util.List;

public record TopLogprob(String token, double logprob, List<Integer> bytes) {}
