package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent tool that applies HSL-based targeted color adjustments to an image.
 */
@DiscoverableTool
public final class AdjustColorTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_color",
            "Selectively recolors specific hue ranges in an image without affecting the rest. "
                    + "Use this to change or enhance specific colors: make a sky more blue, warm up skin tones, "
                    + "shift green foliage toward teal, desaturate a distracting background color, etc. "
                    + "Each adjustment targets a hue range defined by hue_center and hue_width, then applies "
                    + "hue rotation, saturation change, and lightness change with a smooth cosine falloff so "
                    + "transitions are natural. Always pass all adjustments for the image in a single call. "
                    + "Supports JPEG and PNG up to 100MP. "
                    + "Returns { outputSource } on success — use outputSource as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustColorTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustments", description = "Array of hue-targeted adjustments ({hue_center, hue_width, hue_shift, sat_delta, lum_delta}) to apply to the image.")
                    List<ColorAdjustment> adjustments) {

        if (CollectionUtils.isEmpty(adjustments)) {
            return Map.of("error", "adjustments is required");
        }
        final List<double[]> cosLuts = new ArrayList<>();
        for (final ColorAdjustment adj : adjustments) {
            cosLuts.add(ImageUtils.buildCosLut(adj.hueWidth()));
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiledOnPixel(inputFile, pixels -> ImageUtils.applyColorAdjustments(pixels, adjustments, cosLuts)));
    }
}
