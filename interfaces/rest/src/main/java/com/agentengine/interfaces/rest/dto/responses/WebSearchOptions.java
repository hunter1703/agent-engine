package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Web search tool configuration. */
public record WebSearchOptions(
    String searchContextSize, @JsonProperty("user_location") UserLocation userLocation) {}
