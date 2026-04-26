package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Exposure and tone adjustments for an image, modelled after Lightroom's Basic panel.
 * All values are on a [-100, 100] scale where 0 means no change.
 *
 * @param brightness overall brightness offset applied to all channels
 * @param contrast    cubic S-curve contrast: lifts highlights and crushes shadows around a midtone pivot
 * @param highlights  recovery or boost of bright areas (luminance > 0.5)
 * @param shadows     lift or crush of dark areas (luminance < 0.5)
 * @param whites      hard clip point for the brightest tones (luminance > 0.75)
 * @param blacks      hard clip point for the darkest tones (luminance < 0.25)
 * @param saturation  global saturation shift applied across all hues
 */
public record ExposureAdjustment(
        @ToolSchema(name = "brightness", description = "Overall brightness offset applied equally to all channels. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("brightness")
        double brightness,

        @ToolSchema(name = "contrast", description = "Cubic S-curve contrast: positive values lift highlights and crush shadows around a midtone pivot, negative values flatten the image (matte/film look). Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("contrast")
        double contrast,

        @ToolSchema(name = "highlights", description = "Recover blown highlights (negative) or boost bright areas (positive). Affects pixels with luminance above 0.5 with cosine falloff. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("highlights")
        double highlights,

        @ToolSchema(name = "shadows", description = "Lift crushed shadows (positive) or deepen dark areas (negative). Affects pixels with luminance below 0.5 with cosine falloff. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("shadows")
        double shadows,

        @ToolSchema(name = "whites", description = "Shift the white point: positive clips more highlights to white, negative pulls the brightest tones down. Affects pixels with luminance above 0.75. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("whites")
        double whites,

        @ToolSchema(name = "blacks", description = "Shift the black point: negative clips more shadows to black, positive lifts the darkest tones. Affects pixels with luminance below 0.25. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("blacks")
        double blacks,

        @ToolSchema(name = "saturation", description = "Global saturation shift applied across all hues. Positive values intensify colours; negative values desaturate toward greyscale. For targeting specific hue ranges, prefer adjust_color instead. Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("saturation")
        double saturation) {
}
