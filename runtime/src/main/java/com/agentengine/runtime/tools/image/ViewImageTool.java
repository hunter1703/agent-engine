package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;

/**
 * Agent tool that downloads an image and returns it as base64 for visual inspection.
 */
@DiscoverableTool
public final class ViewImageTool extends Tool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "view_image",
            "Downloads an image and returns it as a base64-encoded string so you can inspect its visual "
                    + "content. Use this whenever you need to examine what an image looks like. "
                    + "Returns: { base64: \"<data>\", mimeType: \"<type>\" }.",
            Map.of(),
            ToolRiskLevel.LOW);

    private final CloudStorageService cloudStorageService;

    public ViewImageTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR);
        this.cloudStorageService = cloudStorageService;
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "inputFile", description = "File details of the image to view.") FileDetails inputFile) {
        try {
            if (inputFile == null) {
                return Map.of("error", "inputFile is required");
            }
            try (final InputStream downloadedStream = cloudStorageService.download(inputFile)) {
                final String base64Content = Base64.getEncoder().encodeToString(downloadedStream.readAllBytes());
                return Map.of("base64", base64Content, "mimeType", inputFile.mimeType() != null ? inputFile.mimeType() : "image/jpeg");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
