package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebSearchOptions(
    String searchContextSize, @JsonProperty("user_location") UserLocation userLocation) {}
