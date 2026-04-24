package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single hue-targeted color adjustment.
 *
 * @param hueCenter the center hue of the targeted range, in degrees; valid range [0.0, 360.0)
 * @param hueWidth  the half-width of the targeted hue range, in degrees; valid range (0.0, 180.0]
 * @param hueShift  degrees to rotate the hue of affected pixels; no range restriction
 * @param satDelta  saturation adjustment applied as sat += satDelta * weight * 0.01
 * @param lumDelta  lightness adjustment applied as lum += lumDelta * weight * 0.01
 */
public record ColorAdjustment(
        @ToolSchema(name = "hue_center", description = "Center hue of the targeted range in degrees. Color reference: 0=red, 30=orange, 60=yellow, 90=yellow-green, 120=green, 150=teal, 180=cyan, 210=sky, 240=blue, 270=violet, 300=magenta, 330=pink, 360=red. Valid range: [0.0, 360.0).")
        @JsonProperty("hue_center")
        double hueCenter,

        @ToolSchema(name = "hue_width", description = "Half-width of the targeted hue range in degrees; total coverage is 2 × hue_width. Pixels within hue_width degrees of hue_center are fully weighted; the effect tapers to zero at the edges via cosine falloff. Use 15-30 for surgical targeting (e.g. only pure blues), 45-60 for a natural range (blues and nearby cyans), 90+ for a broad sweep. Valid range: (0.0, 180.0].")
        @JsonProperty("hue_width")
        double hueWidth,

        @ToolSchema(name = "hue_shift", description = "Degrees to rotate the hue of affected pixels. Follows the color wheel order: red(0)→yellow(60)→green(120)→cyan(180)→blue(240)→magenta(300)→red(360). Example: +30 on reds shifts them toward orange; -30 shifts them toward magenta. No range restriction; wraps at 360°.")
        @JsonProperty("hue_shift")
        double hueShift,

        @ToolSchema(name = "sat_delta", description = "Saturation adjustment in percentage points. Effective range: [-100, 100]. +100 fully saturates targeted pixels; -100 fully desaturates them to grey. Use 10-30 for subtle grading, 50+ for dramatic shifts.", optional = true)
        @JsonProperty("sat_delta")
        double satDelta,

        @ToolSchema(name = "lum_delta", description = "Lightness adjustment in percentage points. Effective range: [-100, 100]. +100 drives targeted pixels to white; -100 drives them to black. Use 5-15 for subtle exposure correction, 20-40 for dramatic darkening or brightening.", optional = true)
        @JsonProperty("lum_delta")
        double lumDelta) {
}
