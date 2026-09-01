package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StreamOptions(@JsonProperty("include_usage") Boolean includeUsage) {}
