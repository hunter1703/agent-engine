package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * White balance adjustment: temperature shifts the warm/cool axis, tint shifts the green/magenta axis.
 *
 * @param temperature warm/cool shift; positive = warmer (amber), negative = cooler (blue)
 * @param tint        green/magenta shift; positive = magenta, negative = green
 */
public record TemperatureAdjustment(
        @ToolSchema(name = "temperature", description = "Warm/cool white balance shift. Positive values add warmth (amber/orange cast, like tungsten light); negative values add coolness (blue cast, like shade or overcast). Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("temperature")
        double temperature,

        @ToolSchema(name = "tint", description = "Green/magenta white balance shift. Positive values add a magenta cast; negative values add a green cast. Use to correct fluorescent or mixed-light colour casts. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("tint")
        double tint) {
}
