package com.agentengine.runtime.tools.colorgrading;

import com.agentengine.runtime.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single hue-targeted color adjustment.
 *
 * @param hueCenter the center hue of the targeted range, in degrees; valid range [0.0, 360.0]
 * @param hueWidth  the half-width of the targeted hue range, in degrees; valid range (0.0, 180.0]
 * @param hueShift  degrees to rotate the hue of affected pixels; no range restriction
 * @param satDelta  saturation adjustment applied as sat += satDelta * W * 0.01
 * @param lumDelta  lightness adjustment applied as lum += lumDelta * W * 0.01
 */
public record ColorAdjustment(
        @ToolSchema(name = "hue_center", description = "Center hue of the targeted range in degrees. Valid range: [0.0, 360.0].")
        @JsonProperty("hue_center")
        double hueCenter,

        @ToolSchema(name = "hue_width", description = "Half-width of the targeted hue range in degrees. Controls how broadly the adjustment falls off from the center. Valid range: (0.0, 180.0].")
        @JsonProperty("hue_width")
        double hueWidth,

        @ToolSchema(name = "hue_shift", description = "Degrees to rotate the hue of affected pixels. Positive values shift toward warmer hues, negative toward cooler. No range restriction; wraps at 360°.")
        @JsonProperty("hue_shift")
        double hueShift,

        @ToolSchema(name = "sat_delta", description = "Saturation adjustment for affected pixels, applied as sat += satDelta * weight * 0.01. Positive values increase saturation, negative decrease it.", optional = true)
        @JsonProperty("sat_delta")
        double satDelta,

        @ToolSchema(name = "lum_delta", description = "Lightness adjustment for affected pixels, applied as lum += lumDelta * weight * 0.01. Positive values brighten, negative darken.", optional = true)
        @JsonProperty("lum_delta")
        double lumDelta) {
}
