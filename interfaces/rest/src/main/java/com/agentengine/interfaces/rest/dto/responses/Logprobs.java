package com.agentengine.interfaces.rest.dto.responses;

import java.util.List;

public record Logprobs(List<TokenLogprob> content, List<TokenLogprob> refusal) {}
