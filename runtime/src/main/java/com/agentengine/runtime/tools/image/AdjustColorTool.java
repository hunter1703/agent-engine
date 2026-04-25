package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent tool that applies HSL-based targeted color adjustments to an image.
 * Processes images up to 100MP using a memory-stable tiled architecture with virtual threads.
 */
@DiscoverableTool
public final class AdjustColorTool extends Tool {

    private static final Logger LOG = LoggerFactory.getLogger(AdjustColorTool.class);

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_color",
            "Selectively recolors specific hue ranges in an image without affecting the rest. "
                    + "Use this to change or enhance specific colors: make a sky more blue, warm up skin tones, "
                    + "shift green foliage toward teal, desaturate a distracting background color, etc. "
                    + "Each adjustment targets a hue range defined by hue_center and hue_width, then applies "
                    + "hue rotation, saturation change, and lightness change with a smooth cosine falloff so "
                    + "transitions are natural. Always pass all adjustments for the image in a single call. "
                    + "Supports JPEG and PNG up to 100MP. "
                    + "Returns the output file details on success.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    private final CloudStorageService cloudStorageService;

    public AdjustColorTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR);
        this.cloudStorageService = cloudStorageService;
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Source the input image")
                    String source,
            @ToolSchema(name = "adjustments", description = "Array of hue-targeted adjustments ({hue_center, hue_width, hue_shift, sat_delta, lum_delta}) to apply to the image.")
                    List<ColorAdjustment> adjustments) {

        try {
            try (final InputStream is = cloudStorageService.downloadFromSource(source)) {
                final byte[] header = is.readNBytes(12);
                final String format = ImageUtils.detectFormatFromHeader(header);
                final File inputTempFile = Files.createTempFile("input", "." + format).toFile();
                try {
                    try (final OutputStream os = new FileOutputStream(inputTempFile)) {
                        os.write(header);
                        is.transferTo(os);
                    }
                    final File outputTempFile = applyAdjustments(inputTempFile, adjustments);
                    try {
                        try (final InputStream outputStream = new FileInputStream(outputTempFile)) {
                            final FileDetails outputFileDetails = cloudStorageService.upload(
                                    UUID.randomUUID().toString(), outputStream, outputTempFile.length(), "image/" + format);
                            return Map.of("outputFile", outputFileDetails);
                        }
                    } finally {
                        outputTempFile.delete();
                    }
                } finally {
                    inputTempFile.delete();
                }
            }
        } catch (Exception e) {
            LOG.error("adjust_color failed", e);
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    public static File applyAdjustments(final File inputFile, final List<ColorAdjustment> adjustments) throws IOException {
        final List<double[]> cosLuts = new ArrayList<>();
        for (final ColorAdjustment adj : adjustments) {
            cosLuts.add(ImageUtils.buildCosLut(adj.hueWidth()));
        }
        return ImageUtils.processTiled(inputFile,
                pixels -> ImageUtils.applyColorAdjustments(pixels, adjustments, cosLuts));
    }
}
