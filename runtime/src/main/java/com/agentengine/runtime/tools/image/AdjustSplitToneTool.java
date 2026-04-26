package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.Map;

/**
 * Agent tool that applies split toning — a separate colour tint to shadows and highlights.
 */
@DiscoverableTool
public final class AdjustSplitToneTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_split_tone",
            "Applies split toning: a colour tint to shadows and a separate tint to highlights, with a smooth luminance-based crossover. "
                    + "This is the primary tool for cinematic colour grading — use it to add cool cyan/blue shadows and warm amber highlights, "
                    + "or any other two-tone grade. The balance parameter shifts the crossover point. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success — use outputSource as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustSplitToneTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "Split tone parameters: shadow_hue, shadow_saturation, highlight_hue, highlight_saturation, balance.")
                    SplitToneAdjustment adjustment) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiledOnPixel(inputFile, pixels -> ImageUtils.applySplitTone(pixels, adjustment)));
    }
}
