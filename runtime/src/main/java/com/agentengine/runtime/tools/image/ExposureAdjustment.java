package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Exposure and tone adjustments modelled after a four-zone tone curve.
 * Each zone parameter shifts the output brightness of pixels in that luminance range;
 * the curve is interpolated smoothly between zones so adjustments blend naturally.
 * All zone values are on a [-100, 100] scale where 0 means no change.
 *
 * <p>Tone curve zones (luminance ranges):
 * <ul>
 *   <li>{@code blacks}     — deepest shadows, luminance 0–25%</li>
 *   <li>{@code shadows}    — dark midtones, luminance 25–50%</li>
 *   <li>{@code highlights} — light midtones, luminance 50–75%</li>
 *   <li>{@code whites}     — brightest tones, luminance 75–100%</li>
 * </ul>
 */
public record ExposureAdjustment(
        @ToolSchema(name = "brightness", description = "Global exposure shift applied to all tones before the curve. +30 brightens the whole image; -30 darkens it. Use for overall exposure correction. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("brightness")
        double brightness,

        @ToolSchema(name = "blacks", description = "Tone curve offset for the deepest shadows (luminance 0–25%). +30 lifts blacks toward grey (open, airy look); -30 crushes them toward pure black (deep, rich shadows). Cinematic matte grades typically use +20 to +40 here. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("blacks")
        double blacks,

        @ToolSchema(name = "shadows", description = "Tone curve offset for dark midtones (luminance 25–50%). +30 opens up shadow detail and makes dark areas feel lighter; -30 deepens them. Use +20 to +50 to recover detail in underexposed shadows. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("shadows")
        double shadows,

        @ToolSchema(name = "highlights", description = "Tone curve offset for light midtones (luminance 50–75%). +30 brightens the upper midtones; -30 pulls them down for a more subdued, moody look. Use -20 to -40 to tame overly bright midtones. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("highlights")
        double highlights,

        @ToolSchema(name = "whites", description = "Tone curve offset for the brightest tones (luminance 75–100%). +30 lifts whites (brighter, airier); -30 pulls highlights down (matte, controlled). Use -20 to -40 to recover blown highlights. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("whites")
        double whites,

        @ToolSchema(name = "saturation", description = "Global colour intensity shift applied after the tone curve. +30 makes all colours more vivid; -30 desaturates toward greyscale. For targeting specific hue ranges only, use the colour adjustment tool instead. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("saturation")
        double saturation) {
}
