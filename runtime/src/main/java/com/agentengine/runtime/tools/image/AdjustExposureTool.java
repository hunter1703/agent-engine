package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.Map;

/**
 * Agent tool that adjusts exposure and tone of an image.
 */
@DiscoverableTool
public final class AdjustExposureTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_exposure",
            "Adjusts the exposure and tone of an image using a four-zone tone curve. "
                    + "Each zone independently controls a luminance range: "
                    + "blacks (deepest shadows), shadows (dark midtones), highlights (light midtones), whites (brightest tones). "
                    + "Use brightness for a global exposure shift before the curve. "
                    + "Use saturation for a global colour intensity shift after the curve. "
                    + "All parameters are on a [-100, 100] scale where 0 means no change. "
                    + "IMPORTANT: call this tool at most once per editing session — a second call compounds the effect. "
                    + "Consolidate all tonal decisions into a single call. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success — use outputSource as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustExposureTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "Tone curve parameters: brightness (global), blacks, shadows, highlights, whites (zone offsets), saturation (global).")
                    ExposureAdjustment adjustment) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiledOnPixel(inputFile, pixels -> ImageUtils.applyExposureAdjustments(pixels, adjustment)));
    }
}
