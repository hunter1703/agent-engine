package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;

import java.util.Map;

/**
 * Agent tool that adjusts white balance (temperature and tint) of an image.
 */
@DiscoverableTool
public final class AdjustTemperatureTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_temperature",
            "Corrects or creatively shifts the white balance of an image. "
                    + "All parameter values match Lightroom's White Balance panel exactly — the same Kelvin value and tint integer you enter here produces the same result as in Lightroom. "
                    + "Apply this FIRST — before exposure or tone adjustments — because white balance affects how all other edits look. "
                    + "temperature (Kelvin): 6500K = neutral (D65, the sRGB white point for JPEG/PNG). Lower = cooler/bluer (3200K = tungsten, 4000K = fluorescent, 5500K = direct sunlight). "
                    + "Higher = warmer/more amber (7500K = shade, 9000K = deep blue sky). "
                    + "tint corrects green/magenta casts: positive = add magenta (fix fluorescent green cast), negative = add green. "
                    + "Supports JPEG and PNG up to 100MP. Returns { artifactName } on success — the result is always a PNG file (lossless); pass it as artifactName for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustTemperatureTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "artifactName", description = "Artifact name of the input image.")
                    String artifactName,
            @ToolSchema(name = "adjustment", description = "White balance parameters. temperature in Kelvin (2000–50000, neutral=6500): increase to warm, decrease to cool. tint integer (-150 to +150): positive=add magenta, negative=add green.")
                    TemperatureAdjustment adjustment,
            @ToolSchema(name = "rationale", description = "Describe the lighting condition being corrected or the mood being created (e.g. 'correcting fluorescent green cast', 'adding golden hour warmth', 'making the scene feel cold and clinical').")
                    String rationale,
            ToolContext toolContext) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(artifactName,
                inputFile -> ImageUtils.processTiledOnPixel(inputFile, pixels -> ImageUtils.applyTemperatureAdjustment(pixels, adjustment)),
                toolContext);
    }
}
