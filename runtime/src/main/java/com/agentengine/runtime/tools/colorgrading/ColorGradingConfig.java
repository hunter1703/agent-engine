package com.agentengine.runtime.tools.colorgrading;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Top-level configuration for HSL-based surgical color grading.
 *
 * @param useParallel         when true, tiles are processed in parallel using virtual threads
 * @param tileSize            the width and height of each processing tile in pixels; valid range [64, 4096]
 * @param targetedAdjustments the list of hue-targeted adjustments to apply; must contain at least one entry
 */
public record ColorGradingConfig(
        @JsonProperty("use_parallel") boolean useParallel,
        @JsonProperty("tile_size") int tileSize,
        @JsonProperty("targeted_adjustments") List<TargetedAdjustment> targetedAdjustments) {
}
