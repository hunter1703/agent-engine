package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.Map;

/**
 * Agent tool that adjusts white balance (temperature and tint) of an image.
 */
@DiscoverableTool
public final class AdjustTemperatureTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_temperature",
            "Adjusts the white balance of an image: temperature shifts the warm/cool axis, tint shifts the green/magenta axis. "
                    + "Use temperature to make an image feel warmer (golden hour, candlelight) or cooler (moonlight, shade). "
                    + "Use tint to correct fluorescent or mixed-light colour casts. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success — use outputSource as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustTemperatureTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "White balance parameters: temperature and tint.")
                    TemperatureAdjustment adjustment) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile ->
                ImageUtils.processTiledOnPixel(inputFile, pixels -> ImageUtils.applyTemperatureAdjustment(pixels, adjustment)));
    }
}
