package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Stream options. */
public record StreamOptions(@JsonProperty("include_usage") Boolean includeUsage) {}
