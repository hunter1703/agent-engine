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
                    + "Negative strength darkens edges (classic vignette, draws focus inward). "
                    + "Positive strength lightens edges (bright-edge effect). "
                    + "Use size to control how much of the centre is protected, and feather for transition softness. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AddVignetteTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "Vignette parameters: strength, size, feather.")
                    VignetteAdjustment adjustment) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiled(inputFile,
                        (pixels, tileX, tileY, tileW, tileH, imageW, imageH) ->
                                ImageUtils.applyVignette(pixels, tileX, tileY, tileW, tileH, imageW, imageH, adjustment)));
    }
}
