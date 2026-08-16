package com.agentengine.agent.infra.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Split toning / color grading: applies a colour tint to shadows and a separate tint to highlights.
 * Matches Lightroom's Color Grading panel XMP fields: {@code crs:ColorGradingShadowHue}, {@code
 * crs:ColorGradingShadowSat}, {@code crs:ColorGradingHighlightHue}, {@code
 * crs:ColorGradingHighlightSat}, {@code crs:ColorGradingBalance}.
 *
 * @param shadowHue hue of the shadow tint in degrees [0, 360)
 * @param shadowSaturation strength of the shadow tint, integer [0, 100]
 * @param highlightHue hue of the highlight tint in degrees [0, 360)
 * @param highlightSaturation strength of the highlight tint, integer [0, 100]
 * @param balance crossover shift, integer [-100, 100]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SplitToneAdjustment(
    @ToolSchema(
            name = "shadow_hue",
            description =
                "Hue of the colour tint applied to dark/shadow areas, in degrees [0, 360). "
                    + "Matches Lightroom's Color Grading shadow hue (crs:ColorGradingShadowHue). "
                    + "Color wheel: 0=red, 30=orange, 60=yellow, 120=green, 180=cyan, 210=sky-blue, 240=blue, 270=violet, 300=magenta. "
                    + "Common cinematic grades: 210–240 (cool blue/teal shadows) for the teal-and-orange look; "
                    + "270–300 (violet/purple shadows) for a moody/noir look; "
                    + "30–60 (amber/warm shadows) for a vintage/sepia look.",
            optional = true)
        @JsonProperty("shadow_hue")
        double shadowHue,
    @ToolSchema(
            name = "shadow_saturation",
            description =
                "Strength of the shadow colour tint, integer [0, 100]. "
                    + "Matches Lightroom's Color Grading shadow saturation (crs:ColorGradingShadowSat). "
                    + "0 = no tint applied. 10–20 = subtle, barely perceptible. 30–50 = noticeable cinematic grade. "
                    + "60–80 = strong stylized look. 100 = maximum tint.",
            optional = true)
        @JsonProperty("shadow_saturation")
        int shadowSaturation,
    @ToolSchema(
            name = "highlight_hue",
            description =
                "Hue of the colour tint applied to bright/highlight areas, in degrees [0, 360). "
                    + "Matches Lightroom's Color Grading highlight hue (crs:ColorGradingHighlightHue). "
                    + "Color wheel: 0=red, 30=orange, 60=yellow, 120=green, 180=cyan, 210=sky-blue, 240=blue, 270=violet, 300=magenta. "
                    + "Common cinematic grades: 30–50 (warm amber/orange highlights) for the teal-and-orange look; "
                    + "60 (golden yellow) for a warm summer look; "
                    + "0–15 (warm red/rose) for a romantic/vintage look.",
            optional = true)
        @JsonProperty("highlight_hue")
        double highlightHue,
    @ToolSchema(
            name = "highlight_saturation",
            description =
                "Strength of the highlight colour tint, integer [0, 100]. "
                    + "Matches Lightroom's Color Grading highlight saturation (crs:ColorGradingHighlightSat). "
                    + "0 = no tint applied. 10–20 = subtle warmth or coolness. 30–50 = clear cinematic grade. "
                    + "60–80 = strong stylized look. 100 = maximum tint.",
            optional = true)
        @JsonProperty("highlight_saturation")
        int highlightSaturation,
    @ToolSchema(
            name = "balance",
            description =
                "Shifts the luminance crossover point between shadows and highlights, integer [-100, 100]. "
                    + "Matches Lightroom's Color Grading Balance slider (crs:ColorGradingBalance). "
                    + "0 = crossover at mid-grey (equal shadow/highlight zones). "
                    + "Positive = push crossover toward highlights (shadow tint covers more of the image). "
                    + "Negative = push crossover toward shadows (highlight tint covers more of the image). "
                    + "Typical use: ±10 to ±30 for fine-tuning; leave at 0 for a balanced grade.",
            optional = true)
        @JsonProperty("balance")
        int balance) {}
