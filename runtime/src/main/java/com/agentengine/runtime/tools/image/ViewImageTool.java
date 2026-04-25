package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.genai.types.Blob;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Agent tool that downloads an image and returns it as base64 for visual inspection.
 */
@DiscoverableTool
public final class ViewImageTool extends Tool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "view_image",
            "Downloads an image and returns it for visual inspection. "
                    + "Use this whenever you need to examine what an image looks like.",
            Map.of(),
            ToolRiskLevel.LOW);

    private final CloudStorageService cloudStorageService;

    public ViewImageTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR);
        this.cloudStorageService = cloudStorageService;
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "source", description = "Storage source of the image to view, taken from the source field of the file details.") String source) {
        try {
            try (final InputStream downloadedStream = cloudStorageService.downloadFromSource(source)) {
                final byte[] bytes = downloadedStream.readAllBytes();
                final String mimeType = "image/" + ImageUtils.detectFormatFromHeader(bytes);
                return Map.of("base64", Base64.getEncoder().encodeToString(bytes), "mimeType", mimeType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * Replaces the raw {@code {base64, mimeType}} function-response map with a native
     * {@code inlineData} part so the LLM receives the image through its vision API rather
     * than as a base64 string embedded in JSON.
     *
     * <p>The original {@code FunctionResponse} part is kept (with the binary payload stripped)
     * so the LLM still sees the tool-call/response pairing it expects. The image bytes follow
     * as a sibling {@code inlineData} part.
     *
     * <p>If the response map does not contain the expected {@code base64} key (e.g. it is an
     * error response), the part is returned unchanged.
     */
    @Override
    public List<Part> beforeModelCall(final Part functionResponsePart) {
        final FunctionResponse functionResponse = functionResponsePart.functionResponse().orElse(null);
        if (functionResponse == null) {
            return List.of(functionResponsePart);
        }

        final Map<String, Object> response = functionResponse.response().orElse(Map.of());
        final Object base64Value = response.get("base64");
        final Object mimeTypeValue = response.get("mimeType");

        if (!(base64Value instanceof String base64) || !(mimeTypeValue instanceof String mimeType)) {
            return List.of(functionResponsePart);
        }

        // Strip the binary payload from the function response so the LLM context stays lean.
        final Part strippedResponsePart = Part.builder()
                .functionResponse(functionResponse.toBuilder()
                        .response(Map.of("status", "ok"))
                        .build())
                .build();

        final byte[] bytes = Base64.getDecoder().decode(base64);
        final Part imagePart = Part.fromBytes(bytes, mimeType);

        return List.of(strippedResponsePart, imagePart);
    }
}
