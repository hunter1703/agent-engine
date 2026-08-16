package com.agentengine.agent.infra.tools.image;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Agent tool that applies HSL-based targeted color adjustments to an image. */
@DiscoverableTool
public final class AdjustColorTool extends ImageEditingTool {

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          "adjust_color",
          "Selectively adjusts the hue, saturation, and luminance of specific color ranges in an image. "
              + "Equivalent to Lightroom's HSL/Color Mix panel — all parameter values use Lightroom's scale exactly. "
              + "Apply this THIRD — after white balance and exposure are set. "
              + "Use cases: make a sky more blue and saturated, shift green foliage toward teal, "
              + "warm up skin tones, desaturate a distracting background color, "
              + "shift orange autumn leaves toward red, make a yellow dress more golden. "
              + "Each adjustment targets a hue range (hue_center ± hue_width) with cosine falloff. "
              + "Pass ALL color adjustments for the image in a single call. "
              + "Supports JPEG and PNG up to 100MP. "
              + "Returns { artifactName } on success — the result is always a PNG file (lossless); pass it as artifactName for the next edit.",
          Map.of(),
          ToolRiskLevel.MEDIUM);

  public AdjustColorTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "artifactName", description = "Artifact name of the input image.")
          String artifactName,
      @ToolSchema(
              name = "adjustments",
              description =
                  "Array of hue-targeted adjustments. Each entry: hue_center (degrees, 0=red/30=orange/60=yellow/120=green/180=cyan/240=blue/300=magenta), hue_width (degrees, 30–60 typical), hue_shift (degrees, optional), sat_delta (-100 to +100 integer, optional), lum_delta (-100 to +100 integer, optional).")
          List<ColorAdjustment> adjustments,
      @ToolSchema(
              name = "rationale",
              description =
                  "Describe the color goals (e.g. 'making the sky more vivid blue', 'shifting foliage from yellow-green to teal', 'warming skin tones', 'desaturating the red background').")
          String rationale,
      ToolContext toolContext) {

    if (CollectionUtils.isEmpty(adjustments)) {
      return ToolOutput.direct(Map.of("error", "adjustments is required"));
    }
    final List<double[]> cosLuts = new ArrayList<>();
    for (final ColorAdjustment adj : adjustments) {
      cosLuts.add(ImageUtils.buildCosLut(adj.hueWidth()));
    }
    return processImage(
        artifactName,
        inputFile ->
            ImageUtils.processTiledOnPixel(
                inputFile,
                pixels -> ImageUtils.applyColorAdjustments(pixels, adjustments, cosLuts)),
        toolContext);
  }
}
