package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Split toning: applies a colour tint to shadows and a separate tint to highlights,
 * with a smooth luminance-based crossover. Classic cinematic grading technique.
 *
 * @param shadowHue        hue of the shadow tint in degrees
 * @param shadowSaturation strength of the shadow tint
 * @param highlightHue     hue of the highlight tint in degrees
 * @param highlightSaturation strength of the highlight tint
 * @param balance          shifts the crossover point; positive = more highlights affected, negative = more shadows
 */
public record SplitToneAdjustment(
        @ToolSchema(name = "shadow_hue", description = "Hue of the colour tint applied to dark areas, in degrees. Color reference: 0=red, 30=orange, 60=yellow, 120=green, 180=cyan, 210=sky, 240=blue, 270=violet, 300=magenta. Range [0, 360).", optional = true)
        @JsonProperty("shadow_hue")
        double shadowHue,

        @ToolSchema(name = "shadow_saturation", description = "Strength of the shadow tint. 0 = no tint, 100 = full tint at maximum weight. Range [0, 100].", optional = true)
        @JsonProperty("shadow_saturation")
        double shadowSaturation,

        @ToolSchema(name = "highlight_hue", description = "Hue of the colour tint applied to bright areas, in degrees. Color reference: 0=red, 30=orange, 60=yellow, 120=green, 180=cyan, 210=sky, 240=blue, 270=violet, 300=magenta. Range [0, 360).", optional = true)
        @JsonProperty("highlight_hue")
        double highlightHue,

        @ToolSchema(name = "highlight_saturation", description = "Strength of the highlight tint. 0 = no tint, 100 = full tint at maximum weight. Range [0, 100].", optional = true)
        @JsonProperty("highlight_saturation")
        double highlightSaturation,

        @ToolSchema(name = "balance", description = "Shifts the luminance crossover point between shadows and highlights. Positive = more of the image is treated as highlights (tint spreads into midtones); negative = more is treated as shadows. Range [-100, 100]; 0 = crossover at mid-grey.", optional = true)
        @JsonProperty("balance")
        double balance) {
}
