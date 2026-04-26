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

        @ToolSchema(name = "size", description = "Radius of the protected centre zone as a percentage of the image half-diagonal. 50 = moderate centre protection (vignette starts halfway to the corners); 80 = large clear centre (vignette only at the very edges); 20 = tight centre (vignette starts close to the middle). Range [0, 100].", optional = true)
        @JsonProperty("size")
        double size,

        @ToolSchema(name = "feather", description = "Softness of the transition from the clear centre to the vignetted edge. 70–90 = long, smooth gradient (natural, photographic); 20–40 = harder edge (more graphic). Range [0, 100].", optional = true)
        @JsonProperty("feather")
        double feather) {
}
