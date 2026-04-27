package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single hue-targeted color adjustment, equivalent to one color channel in Lightroom's
 * HSL/Color Mix panel (crs:HueAdjustmentRed, crs:SaturationAdjustmentRed, etc.)
 * but with explicit hue targeting by center and width rather than fixed color buckets.
 *
 * <p>hue_shift is in degrees (not Lightroom's -100/+100 UI scale) because degrees are
 * unambiguous and model-friendly: the model can reason "shift reds 30° toward orange"
 * without needing to know Lightroom's internal mapping.
 *
 * @param hueCenter the center hue of the targeted range, in degrees [0, 360)
 * @param hueWidth  the half-width of the targeted hue range, in degrees (0, 180]
 * @param hueShift  hue rotation of affected pixels in degrees [-180, 180]
 * @param satDelta  saturation change, integer [-100, 100] (matches crs:SaturationAdjustment*)
 * @param lumDelta  luminance change, integer [-100, 100] (matches crs:LuminanceAdjustment*)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColorAdjustment(
        @ToolSchema(name = "hue_center", description = "Center hue of the targeted range in degrees. "
                + "Use the standard color wheel: 0=red, 30=orange, 60=yellow, 90=chartreuse, 120=green, "
                + "150=spring-green, 180=cyan, 210=azure/sky-blue, 240=blue, 270=violet, 300=magenta, 330=rose/pink. "
                + "Valid range: [0.0, 360.0).")
        @JsonProperty("hue_center")
        double hueCenter,

        @ToolSchema(name = "hue_width", description = "Half-width of the targeted hue range in degrees. "
                + "Total affected range is 2 × hue_width centered on hue_center, with cosine falloff at the edges. "
                + "15–25 = surgical (only the exact hue, e.g. isolate a specific shade of blue sky). "
                + "30–45 = natural (a color and its close neighbors, e.g. blues and nearby cyans). "
                + "60–90 = broad (a whole color family, e.g. all warm tones). "
                + "Valid range: (0.0, 180.0].")
        @JsonProperty("hue_width")
        double hueWidth,

        @ToolSchema(name = "hue_shift", description = "Degrees to rotate the hue of affected pixels along the colour wheel. "
                + "Positive = clockwise (e.g. red→orange→yellow); negative = counter-clockwise (e.g. red→magenta→violet). "
                + "Examples: +30 on reds (hue_center=0) shifts them toward orange; "
                + "-30 on greens (hue_center=120) shifts them toward yellow-green. "
                + "Range: [-180, 180]; 0 = no hue change.", optional = true)
        @JsonProperty("hue_shift")
        double hueShift,

        @ToolSchema(name = "sat_delta", description = "Saturation change for targeted pixels, integer [-100, 100]. "
                + "Matches Lightroom's HSL Saturation sliders (crs:SaturationAdjustment*). "
                + "+50 makes targeted colors more vivid; -50 mutes them; -100 fully desaturates to grey. "
                + "Typical use: +20 to +40 to pop a specific color, -30 to -60 to mute a distracting color. "
                + "0 = no saturation change.", optional = true)
        @JsonProperty("sat_delta")
        int satDelta,

        @ToolSchema(name = "lum_delta", description = "Luminance (brightness) change for targeted pixels, integer [-100, 100]. "
                + "Matches Lightroom's HSL Luminance sliders (crs:LuminanceAdjustment*). "
                + "+20 brightens targeted pixels; -20 darkens them. "
                + "Typical use: -15 to darken a sky without affecting other colors; +10 to lift skin tones. "
                + "0 = no luminance change.", optional = true)
        @JsonProperty("lum_delta")
        int lumDelta) {
}
