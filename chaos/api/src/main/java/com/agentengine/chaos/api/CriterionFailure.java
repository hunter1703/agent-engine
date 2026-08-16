package com.agentengine.chaos.api;

public record CriterionFailure(
    CriterionType type, double threshold, double actual, String description) {}
