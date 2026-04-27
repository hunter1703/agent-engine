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
            "Applies a cinematic colour grade by tinting shadows and highlights with different hues. "
                    + "Apply this FOURTH — after white balance, exposure, and color corrections are done. "
                    + "This is the primary tool for creative colour grading and mood. "
                    + "Classic grades: teal-and-orange (shadow_hue=210, highlight_hue=35) for blockbuster cinema; "
                    + "blue-and-gold (shadow_hue=240, highlight_hue=50) for cold/warm contrast; "
                    + "violet-and-amber (shadow_hue=280, highlight_hue=40) for moody/noir; "
                    + "sepia (shadow_hue=30, highlight_hue=40) for vintage/warm. "
                    + "Start with low saturation (15–30) and increase to taste. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success — use outputSource as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustSplitToneTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "Color grade parameters. shadow_hue/highlight_hue: degrees (0=red, 30=orange, 60=yellow, 120=green, 180=cyan, 210=sky-blue, 240=blue, 270=violet, 300=magenta). shadow_saturation/highlight_saturation: tint strength 0–100 (0=none, 20=subtle, 50=strong). balance: crossover shift -100 to +100 (0=balanced).")
                    SplitToneAdjustment adjustment,
            @ToolSchema(name = "rationale", description = "Describe the mood or cinematic look being created (e.g. 'teal-and-orange blockbuster grade', 'cold blue shadows for a thriller feel', 'warm golden highlights for a summer look', 'vintage sepia tone').")
                    String rationale) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiledOnPixel(inputFile, pixels -> ImageUtils.applySplitTone(pixels, adjustment)));
    }
}
