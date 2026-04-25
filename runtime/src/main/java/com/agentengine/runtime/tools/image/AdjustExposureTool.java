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
import java.util.Map;
import java.util.UUID;

/**
 * Agent tool that adjusts exposure and tone of an image.
 * Controls brightness, contrast, highlights, shadows, whites, and blacks
 * using the same memory-stable tiled architecture as the color grading tool.
 */
@DiscoverableTool
public final class AdjustExposureTool extends Tool {

    private static final Logger LOG = LoggerFactory.getLogger(AdjustExposureTool.class);

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "adjust_exposure",
            "Adjusts the exposure and tone of an image: brightness, contrast, highlights, shadows, whites, and blacks. "
                    + "All parameters are on a [-100, 100] scale where 0 means no change. "
                    + "Use brightness for overall lightness, contrast for punch, highlights/shadows for tonal recovery, "
                    + "and whites/blacks to set the clipping points. "
                    + "Supports JPEG and PNG up to 100MP. Returns the output file details on success.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    private final CloudStorageService cloudStorageService;

    public AdjustExposureTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR);
        this.cloudStorageService = cloudStorageService;
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Source of the input image")
                    String source,
            @ToolSchema(name = "adjustment", description = "Exposure and tone parameters: brightness, contrast, highlights, shadows, whites, blacks.")
                    ExposureAdjustment adjustment) {

        try {
            if (adjustment == null) {
                return Map.of("error", "adjustment is required");
            }
            try (final InputStream is = cloudStorageService.downloadFromSource(source)) {
                final byte[] header = is.readNBytes(12);
                final String format = ImageUtils.detectFormatFromHeader(header);
                final File inputTempFile = Files.createTempFile("input", "." + format).toFile();
                try {
                    try (final OutputStream os = new FileOutputStream(inputTempFile)) {
                        os.write(header);
                        is.transferTo(os);
                    }
                    final File outputTempFile = applyAdjustment(inputTempFile, adjustment);
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
            LOG.error("adjust_exposure failed", e);
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    public static File applyAdjustment(final File inputFile, final ExposureAdjustment adjustment) throws IOException {
        return ImageUtils.processTiled(inputFile,
                pixels -> ImageUtils.applyExposureAdjustments(pixels, adjustment));
    }
}
