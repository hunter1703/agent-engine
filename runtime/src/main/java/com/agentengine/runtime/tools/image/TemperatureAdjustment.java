package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * White balance adjustment using industry-standard Kelvin temperature and integer tint,
 * matching Lightroom/Camera Raw XMP fields {@code crs:Temperature} and {@code crs:Tint}.
 *
 * <p>Neutral is 6500K (D65), the white point of the sRGB colour space used by JPEG/PNG images.
 * Passing 6500K with tint=0 is a no-op.
 *
 * @param temperature colour temperature in Kelvin; 6500K is neutral (D65/sRGB white point)
 * @param tint        green/magenta shift, integer [-150, 150]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemperatureAdjustment(
        @ToolSchema(name = "temperature", description = "Colour temperature in Kelvin (integer). "
                + "Matches Lightroom's Temperature slider (crs:Temperature). "
                + "Neutral for rendered JPEG/PNG images = 6500K (D65, the sRGB white point). "
                + "Cooler/bluer: 2000K = candlelight/fire, 3200K = tungsten/incandescent, 4000K = fluorescent, 5500K = direct sunlight. "
                + "Warmer/more amber: 7500K = open shade, 9000K = deep blue sky. "
                + "Practical editing range: 2500–9000K. "
                + "To warm an image: increase above 6500. To cool: decrease below 6500. "
                + "Range: 2000–50000; neutral: 6500.", optional = true)
        @JsonProperty("temperature")
        int temperature,

        @ToolSchema(name = "tint", description = "Green/magenta white balance correction, integer [-150, 150]. "
                + "Matches Lightroom's Tint slider (crs:Tint). "
                + "Positive = add magenta (corrects green cast from fluorescent lights). "
                + "Negative = add green (corrects magenta cast). "
                + "Most images need only small corrections: ±5 to ±15 for subtle casts, ±30 to ±60 for strong fluorescent correction. "
                + "0 = no tint correction.", optional = true)
        @JsonProperty("tint")
        int tint) {
}
