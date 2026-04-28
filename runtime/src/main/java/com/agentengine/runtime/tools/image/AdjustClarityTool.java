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
 * Agent tool that adjusts clarity — negative values create a dreamy glow effect,
 * positive values increase local contrast for a crisp, sharp look.
 *
 * <p>Unlike the other image tools, clarity operates on the full image (not tiled) because
 * the Gaussian blur it uses requires spatial context across tile boundaries.
 */
@DiscoverableTool
public final class AdjustClarityTool extends ImageEditingTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_clarity",
            "Adjusts midtone local contrast (clarity/texture). "
                    + "Matches Lightroom's Clarity slider exactly — the same value here produces the same result as in Lightroom. "
                    + "Apply this FIFTH — after tonal and color work, before vignette. "
                    + "Positive clarity: increases local contrast for a crisp, sharp, punchy, detailed look. "
                    + "Use for landscapes, architecture, product shots, dramatic portraits. "
                    + "Negative clarity: creates a soft, dreamy, glowing, ethereal look by reducing local contrast. "
                    + "Use for romantic portraits, misty scenes, soft beauty shots, atmospheric landscapes. "
                    + "Typical values: +15 to +30 for mild crispness; +40 to +60 for punchy/dramatic; "
                    + "-15 to -25 for subtle softness; -30 to -50 for dreamy glow; -60 to -80 for heavy mist/atmosphere. "
                    + "Supports JPEG and PNG. Returns { outputSource } on success — outputSource is always a PNG file (lossless); use it as the source for the next edit.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public AdjustClarityTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, cloudStorageService);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the input image.")
                    String source,
            @ToolSchema(name = "clarity", description = "Clarity amount. Negative = soft/dreamy/glow (range [-100, 0]): -15 subtle softness, -30 noticeable glow, -50 heavy atmosphere, -80 extreme mist. Positive = crisp/sharp/punchy (range [0, 100]): 20 mild crispness, 40 punchy, 60 very sharp. 0 = no change.")
                    double clarity,
            @ToolSchema(name = "rationale", description = "Brief explanation of why this adjustment is being applied and what visual effect it is intended to achieve.")
                    String rationale) {

        if (clarity == 0.0) {
            return Map.of("outputSource", source);
        }
        return processImage(source, inputFile -> {
            try {
                return applyClarity(inputFile, clarity);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static File applyClarity(final File inputFile, final double clarity) throws IOException {
        final BufferedImage raw = ImageIO.read(inputFile);
        if (raw == null) {
            throw new IllegalArgumentException("Could not read image: " + inputFile.getName());
        }

        final BufferedImage image;
        if (raw.getType() == BufferedImage.TYPE_INT_RGB) {
            image = raw;
        } else {
            image = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            final Graphics2D g2d = image.createGraphics();
            g2d.drawImage(raw, 0, 0, null);
            g2d.dispose();
        }

        ImageUtils.applyClarity(image, clarity);

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
