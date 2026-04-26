package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.tools.BinaryDataTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Base class for all image editing tools.
 * <p>
 * Subclasses call {@link #processImage(String, Function)} from their {@code execute} method,
 * passing the cloud storage source and a function that receives the downloaded temp file and
 * returns the processed output temp file. This class handles everything else: download,
 * format detection, temp file lifecycle, upload, and error handling.
 */
public abstract class ImageEditingTool extends BinaryDataTool {

    private static final Logger LOG = LoggerFactory.getLogger(ImageEditingTool.class);

    private final CloudStorageService cloudStorageService;

    protected ImageEditingTool(final ToolDescriptor descriptor, final CloudStorageService cloudStorageService) {
        super(descriptor, false, cloudStorageService);
        this.cloudStorageService = cloudStorageService;
    }

    /**
     * Download the image at {@code source}, apply {@code adjustment}, upload the result, and return
     * {@code { outputSource }} on success or {@code { error }} on failure.
     *
     * @param source     cloud storage source of the input image
     * @param adjustment function that receives the input temp file and returns the processed output temp file;
     *                   the output file will be deleted after upload
     */
    protected Map<String, Object> processImage(final String source, final Function<File, File> adjustment) {
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
                    final File outputTempFile = adjustment.apply(inputTempFile);
                    try {
                        try (final InputStream outputStream = new FileInputStream(outputTempFile)) {
                            final FileDetails result = cloudStorageService.upload(
                                    UUID.randomUUID().toString(), outputStream, outputTempFile.length(), "image/" + format);
                            return Map.of("outputSource", result.source());
                        }
                    } finally {
                        outputTempFile.delete();
                    }
                } finally {
                    inputTempFile.delete();
                }
            }
        } catch (Exception e) {
            LOG.error("{} failed for source={}", descriptor().name(), source, e);
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
