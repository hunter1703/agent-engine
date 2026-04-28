package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Agent tool that adjusts exposure and tone of an image.
 */
@DiscoverableTool
public final class AdjustExposureTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_exposure",
            "Sets the overall brightness and tonal balance of an image. "
                    + "All parameter values match Lightroom's Basic panel exactly — the same number you enter here produces the same result as in Lightroom. "
                    + "Apply this SECOND — after white balance, before color grading. "
                    + "exposure (EV stops): controls overall midtone brightness — use it first to set the overall exposure. "
                    + "+1.0 = one stop brighter (doubles brightness), -1.0 = one stop darker. "
                    + "blacks: negative crushes blacks to pure black (deep contrast); positive lifts blacks (faded/matte/film look). "
                    + "shadows: positive opens up dark areas without affecting highlights; negative deepens shadows. "
                    + "highlights: negative recovers blown highlights and adds drama; positive brightens for airy look. "
                    + "whites: positive sets a clean white point (more contrast); negative pulls whites down (matte look). "
                    + "saturation: global colour intensity — use adjust_color for targeted hue-specific changes. "
                    + "Supports JPEG and PNG up to 100MP. Returns { outputSource } on success — outputSource is always a PNG file (lossless); use it as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustExposureTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "adjustment", description = "Tone parameters. exposure: EV stops (-5.0 to +5.0, 0=no change). blacks/shadows/highlights/whites: zone offsets (-100 to +100 integers, 0=no change). saturation: global colour intensity (-100 to +100 integer, 0=no change).")
                    ExposureAdjustment adjustment,
            @ToolSchema(name = "rationale", description = "Describe the tonal goal (e.g. 'image is 1.5 stops underexposed', 'lifting blacks for a cinematic matte look', 'recovering blown sky highlights', 'creating a moody low-key portrait').")
                    String rationale) {

        if (adjustment == null) {
            return Map.of("error", "adjustment is required");
        }
        return processImage(source, inputFile -> {
            try {
                return applyExposure(inputFile, adjustment);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static File applyExposure(final File inputFile, final ExposureAdjustment adj) throws IOException {
        final BufferedImage raw = ImageIO.read(inputFile);
        if (raw == null) throw new IllegalArgumentException("Could not read image: " + inputFile.getName());

        final BufferedImage image;
        if (raw.getType() == BufferedImage.TYPE_INT_RGB) {
            image = raw;
        } else {
            image = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            final Graphics2D g2d = image.createGraphics();
            g2d.drawImage(raw, 0, 0, null);
            g2d.dispose();
        }

        // Compute the Gaussian base layer on the full image — required for local-adaptation
        // shadows/highlights. Must be done before tiling since it needs global context.
        final float[] baseLayer = (adj.shadows() != 0 || adj.highlights() != 0)
                ? ImageUtils.computeBaseLayer(image)
                : null;

        final int w = image.getWidth();
        // Apply per-tile, passing the base layer slice for each tile's absolute pixel positions
        ImageUtils.processTiledInPlace(image, (pixels, tileX, tileY, tileW, tileH) -> {
            // Extract the base layer values for this tile's absolute pixel positions
            final float[] tileBase = (baseLayer != null) ? new float[tileW * tileH] : null;
            if (tileBase != null) {
                for (int row = 0; row < tileH; row++) {
                    System.arraycopy(baseLayer, (tileY + row) * w + tileX, tileBase, row * tileW, tileW);
                }
            }
            ImageUtils.applyExposureAdjustments(pixels, adj, tileBase);
        });

        final File outputFile = Files.createTempFile("processed", ".png").toFile();
        try {
            ImageUtils.writeImage(image, "png", outputFile);
        } catch (Exception e) {
            outputFile.delete();
            throw e;
        }
        return outputFile;
    }
}
