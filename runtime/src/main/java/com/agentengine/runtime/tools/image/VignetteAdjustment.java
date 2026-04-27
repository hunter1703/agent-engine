package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vignette: darkens or lightens image edges with an elliptical cosine falloff,
 * with optional saturation adjustment at the edges.
 * Matches Lightroom's Effects panel XMP fields {@code crs:VignetteAmount} and
 * {@code crs:VignetteMidpoint}. The saturation parameter is inspired by darktable's
 * vignette module (GPL), which independently controls edge saturation.
 *
 * @param amount    darkening (negative) or lightening (positive) of edges; integer [-100, 100]
 * @param midpoint  how far from centre the vignette starts; integer [0, 100]
 * @param feather   softness of the transition; integer [0, 100]
 * @param saturation saturation change at the vignette edges; integer [-100, 100]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VignetteAdjustment(
        @ToolSchema(name = "amount", description = "Darkening or lightening of image edges, integer [-100, 100]. "
                + "Matches Lightroom's Vignetting Amount (crs:VignetteAmount). "
                + "Negative = darken edges (classic vignette — draws the eye inward toward the subject). "
                + "Positive = lighten edges (bright-edge / high-key effect). "
                + "Typical portrait vignette: -15 to -30 (subtle) or -40 to -60 (strong). "
                + "Dramatic dark vignette: -70 to -100. "
                + "0 = no vignette.", optional = true)
        @JsonProperty("amount")
        int amount,

        @ToolSchema(name = "midpoint", description = "Controls how far from the image centre the vignette begins, integer [0, 100]. "
                + "Matches Lightroom's Vignetting Midpoint (crs:VignetteMidpoint). "
                + "Higher = larger clear centre zone (vignette only at the very edges). "
                + "Lower = smaller clear centre (vignette starts closer to the subject). "
                + "Typical values: 30–40 = tight vignette starting near centre; "
                + "50–60 = balanced (vignette starts halfway to corners); "
                + "70–85 = subtle edge darkening only. "
                + "Default: 50.", optional = true)
        @JsonProperty("midpoint")
        int midpoint,

        @ToolSchema(name = "feather", description = "Softness of the transition from clear centre to vignetted edge, integer [0, 100]. "
                + "Higher = longer, smoother gradient (natural, photographic look). "
                + "Lower = harder, more abrupt edge (graphic/stylized look). "
                + "Typical values: 50–70 = natural soft gradient; 80–100 = very gradual, barely perceptible transition; "
                + "20–40 = harder edge for a more dramatic effect. "
                + "Default: 50.", optional = true)
        @JsonProperty("feather")
        int feather,

        @ToolSchema(name = "saturation", description = "Saturation change applied at the vignette edges, integer [-100, 100]. "
                + "Negative = desaturate the edges (classic darkroom look — dark desaturated corners draw focus to the colourful centre). "
                + "Positive = increase saturation at the edges. "
                + "Typical use: -20 to -50 for a natural desaturated vignette; 0 = no saturation change. "
                + "Only applied where the vignette effect is active (outside the midpoint radius).", optional = true)
        @JsonProperty("saturation")
        int saturation) {
}
