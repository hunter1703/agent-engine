package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vignette: darkens or lightens image edges with an elliptical cosine falloff.
 *
 * @param strength how much to darken (negative) or lighten (positive) the edges
 * @param size     radius of the clear centre zone as a fraction of the image half-diagonal
 * @param feather  softness of the transition from clear centre to vignetted edge
 */
public record VignetteAdjustment(
        @ToolSchema(name = "strength", description = "How much to darken or lighten the image edges. Negative values darken (classic vignette); positive values lighten (bright-edge effect). Range [-100, 100]; 0 = no change.", optional = true)
        @JsonProperty("strength")
        double strength,

        @ToolSchema(name = "size", description = "Radius of the protected centre zone, as a percentage of the image half-diagonal. Higher values push the vignette further toward the edges, leaving more of the image unaffected. Range [0, 100]; default 50.", optional = true)
        @JsonProperty("size")
        double size,

        @ToolSchema(name = "feather", description = "Softness of the transition from the clear centre to the vignetted edge. Higher values create a longer, smoother gradient; lower values create a harder edge. Range [0, 100]; default 50.", optional = true)
        @JsonProperty("feather")
        double feather) {
}
