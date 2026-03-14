package com.agentengine.interfaces.rest.dto.responses;

/**
 * User location for localized web search results.
 */
public record UserLocation(String type, String timezone, String city, String region, String country) {
}
