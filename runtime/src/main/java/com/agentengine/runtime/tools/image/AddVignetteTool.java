package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.Map;

/**
 * Agent tool that adds a vignette effect to an image.
 */
@DiscoverableTool
public final class AddVignetteTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "add_vignette",
            "Adds a vignette effect: darkens or lightens image edges with an elliptical cosine falloff. "
                    + "Matches Lightroom's Effects panel vignette exactly — all parameter values use Lightroom's scale. "
                    + "Negative amount darkens edges (classic vignette, draws focus inward). "
                    + "Positive amount lightens edges (bright-edge effect). "
                    + "Optional saturation control desaturates the edges for a classic darkroom look. "
                    + "Matches Lightroom XMP fields crs:VignetteAmount and crs:VignetteMidpoint. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success — outputSource is always a PNG file (lossless); use it as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AddVignetteTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "Vignette parameters: amount (integer -100 to +100), midpoint (integer 0–100), feather (integer 0–100), saturation (integer -100 to +100, optional, negative=desaturate edges).")
                    VignetteAdjustment adjustment,
            @ToolSchema(name = "rationale", description = "Brief explanation of why this adjustment is being applied and what visual effect it is intended to achieve.")
                    String rationale) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiled(inputFile,
                        (pixels, tileX, tileY, tileW, tileH, imageW, imageH) ->
                                ImageUtils.applyVignette(pixels, tileX, tileY, tileW, tileH, imageW, imageH, adjustment)));
    }
}
