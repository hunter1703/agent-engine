package com.agentengine.runtime.tools.colorgrading;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.IOUtils;
import com.agentengine.util.common.StructuredConcurrencyUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Agent tool that applies surgical HSL-based color grading to an image.
 * Processes images up to 100MP using a memory-stable tiled architecture with virtual threads.
 */
@DiscoverableTool
public final class ColorGradingTool extends Tool {

    private static final Logger LOG = LoggerFactory.getLogger(ColorGradingTool.class);

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "color_grade_image",
            "Applies surgical HSL-based color grading to an image file. Accepts an input image path "
                    + "and a JSON config describing one or more targeted hue adjustments. Each adjustment "
                    + "targets a hue range (hue_center + hue_width) and applies hue rotation, saturation "
                    + "delta, and lightness delta with a smooth cosine falloff. Processes images up to 100MP "
                    + "using a memory-stable tiled architecture. Supports JPEG and PNG. "
                    + "Returns the output image path and duration on success.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public ColorGradingTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(CloudStorageService cloudStorageService,
            @ToolSchema(name = "inputFile", description = "File details for the input image on which adjustments need to be applied") FileDetails inputFile,
            @ToolSchema(name = "config", description = "adjustments (array of {hue_center, hue_width, hue_shift, sat_delta, lum_delta}) to be applied to the image.") List<ColorAdjustment> adjustments) {

        try {
            if (inputFile == null) {
                return Map.of("error", "input file details is needed");
            }

            final File file = Files.createTempFile("input", ".tmp").toFile();
            try(final InputStream is = cloudStorageService.download(inputFile); final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IOUtils.copy(is, fileOutputStream);
            }

            final File outputFile = applyAdjustments(file, adjustments);
            final FileDetails outputFileDetails = cloudStorageService.upload(UUID.randomUUID().toString(), new FileInputStream(outputFile), -1L, "application/image");
            return Map.of("outputFile", outputFileDetails);

        } catch (Exception e) {
            LOG.error("color_grade_image failed", e);
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    public static File applyAdjustments(final File inputFile, final List<ColorAdjustment> adjustments) throws IOException {
        final String format = ColorGradingUtils.getFileFormat(inputFile.getName());

        // get image dimensions without loading all pixel data
        final int[] dimensions = ColorGradingUtils.readDimensions(inputFile, format);
        final int width = dimensions[0];
        final int height = dimensions[1];

        LOG.info("Processing image {}x{}", width, height);

        // generate all tile rectangles up front
        final List<Rectangle> tiles = ColorGradingUtils.buildTiles(width, height);
        LOG.info("Processing {} tiles", tiles.size());

        // pre-compute cosine LUTs — one per adjustment, done once before any tile work
        final List<double[]> cosLuts = new ArrayList<>();
        for (final ColorAdjustment adj : adjustments) {
            cosLuts.add(ColorGradingUtils.buildCosLut(adj.hueWidth()));
        }

        // allocate single output buffer at full image dimensions
        final BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final List<Callable<Void>> callables = new ArrayList<>();
        for (final Rectangle tile : tiles) {
            callables.add(() -> {
                // each virtual thread gets its own ImageReader — ImageReader is not thread-safe
                final ImageReader reader = ColorGradingUtils.getImageReader(format);
                try (final ImageInputStream stream = ImageIO.createImageInputStream(inputFile)) {
                    reader.setInput(stream, false, true);
                    final BufferedImage normalized = readAndNormalizeTile(reader, tile);

                    // Direct DataBufferInt access gives a raw int[] into the image's backing store with no
                    // per-pixel method call overhead. getRGB/setRGB route through ColorModel conversion on
                    // every call — safe for arbitrary image types but ~10x slower at 100MP scale. This cast
                    // is guaranteed safe because readAndNormalizeTile always produces TYPE_INT_RGB.
                    final int[] pixels = ((DataBufferInt) normalized.getRaster().getDataBuffer()).getData();
                    ColorGradingUtils.applyAdjustments(pixels, adjustments, cosLuts);

                    // write processed tile back to output at correct offset
                    synchronized (outputImage) {
                        final Graphics2D g2d = outputImage.createGraphics();
                        g2d.drawImage(normalized, tile.x, tile.y, null);
                        g2d.dispose();
                    }
                    return null;
                } finally {
                    reader.dispose();
                }
            });
        }
        StructuredConcurrencyUtils.runConcurrently(callables);

        // write output
        final File outputFile = Files.createTempFile("processed", "." + format).toFile();
        ImageIO.write(outputImage, format, outputFile);
        return outputFile;
    }


    private static BufferedImage readAndNormalizeTile(final ImageReader reader, final Rectangle tile) throws IOException {

        final ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(tile);
        final BufferedImage rawTile = reader.read(0, param);

        if (rawTile.getType() == BufferedImage.TYPE_INT_RGB) {
            return rawTile;
        }
        // ImageIO decoders return varying BufferedImage types depending on format and color profile —
        // a JPEG may come back as TYPE_3BYTE_BGR, a PNG with transparency as TYPE_4BYTE_ABGR, etc.
        // DataBufferInt only backs TYPE_INT_* images; casting on any other type throws ClassCastException.
        // Redrawing into a fresh TYPE_INT_RGB buffer normalizes the representation unconditionally.
        final BufferedImage normalized =
                new BufferedImage(tile.width, tile.height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2d = normalized.createGraphics();
        g2d.drawImage(rawTile, 0, 0, null);
        g2d.dispose();
        return normalized;
    }

}
