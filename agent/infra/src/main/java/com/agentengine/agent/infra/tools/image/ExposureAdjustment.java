package com.agentengine.agent.infra.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Exposure and tone adjustments matching Lightroom/Camera Raw Process Version 2012 (PV2012) XMP
 * fields: {@code crs:Exposure2012}, {@code crs:Blacks2012}, {@code crs:Shadows2012}, {@code
 * crs:Highlights2012}, {@code crs:Whites2012}, {@code crs:Saturation}.
 *
 * <p>Editing order within this tool: exposure → tone curve (blacks/shadows/highlights/whites) →
 * saturation.
 *
 * <p>Tone curve zones (linear luminance ranges):
 *
 * <ul>
 *   <li>{@code blacks} — deepest shadows, 0–25% luminance
 *   <li>{@code shadows} — dark midtones, 25–50% luminance
 *   <li>{@code highlights} — light midtones, 50–75% luminance
 *   <li>{@code whites} — brightest tones, 75–100% luminance
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExposureAdjustment(
    @ToolSchema(
            name = "exposure",
            description =
                "Global exposure shift in EV (stops), real number. "
                    + "Matches Lightroom's Exposure slider (crs:Exposure2012). "
                    + "Each +1.0 EV doubles brightness (one camera stop brighter); each -1.0 EV halves it. "
                    + "Use this first to set overall brightness before fine-tuning zones. "
                    + "Typical corrections: ±0.3 subtle lift/darken, ±0.7 noticeable, ±1.5 strong, ±2.0 extreme. "
                    + "Range: -5.0 to +5.0; 0.0 = no change.",
            optional = true)
        @JsonProperty("exposure")
        double exposure,
    @ToolSchema(
            name = "blacks",
            description =
                "Tone curve offset for the deepest shadows (0–25% luminance), integer. "
                    + "Matches Lightroom's Blacks slider (crs:Blacks2012). "
                    + "Positive = lift blacks toward grey (faded/matte/film look, open shadows). "
                    + "Negative = crush blacks toward pure black (deep contrast, rich shadows). "
                    + "Cinematic matte grades: +20 to +50. Deep moody look: -20 to -40. "
                    + "Range: -100 to +100; 0 = no change.",
            optional = true)
        @JsonProperty("blacks")
        int blacks,
    @ToolSchema(
            name = "shadows",
            description =
                "Tone curve offset for dark midtones (25–50% luminance), integer. "
                    + "Matches Lightroom's Shadows slider (crs:Shadows2012). "
                    + "Positive = open up shadow detail, reveal texture in dark areas. "
                    + "Negative = deepen shadows for drama. "
                    + "Typical use: +20 to +50 to recover underexposed shadow detail; -15 to -30 for moody look. "
                    + "Range: -100 to +100; 0 = no change.",
            optional = true)
        @JsonProperty("shadows")
        int shadows,
    @ToolSchema(
            name = "highlights",
            description =
                "Tone curve offset for light midtones (50–75% luminance), integer. "
                    + "Matches Lightroom's Highlights slider (crs:Highlights2012). "
                    + "Negative = pull down bright areas, recover overexposed highlights, add drama. "
                    + "Positive = push highlights brighter, airy high-key look. "
                    + "Typical use: -20 to -60 to recover blown highlights; +10 to +30 for bright airy look. "
                    + "Range: -100 to +100; 0 = no change.",
            optional = true)
        @JsonProperty("highlights")
        int highlights,
    @ToolSchema(
            name = "whites",
            description =
                "Tone curve offset for the brightest tones (75–100% luminance), integer. "
                    + "Matches Lightroom's Whites slider (crs:Whites2012). "
                    + "Positive = push the white point up (brighter, more contrast). "
                    + "Negative = pull whites down (matte, controlled, no clipping). "
                    + "Typical use: +10 to +30 to set a clean white point; -20 to -40 for matte/film look. "
                    + "Range: -100 to +100; 0 = no change.",
            optional = true)
        @JsonProperty("whites")
        int whites,
    @ToolSchema(
            name = "saturation",
            description =
                "Global colour intensity shift, integer. "
                    + "Matches Lightroom's Saturation slider (crs:Saturation). "
                    + "Positive = all colours more vivid and punchy. Negative = desaturate toward greyscale. "
                    + "For targeting only specific hue ranges, use adjust_color instead. "
                    + "Typical use: +10 to +25 for a vibrant look; -15 to -30 for a muted/film look; -100 for B&W. "
                    + "Range: -100 to +100; 0 = no change.",
            optional = true)
        @JsonProperty("saturation")
        int saturation) {}
