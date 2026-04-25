package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.models.LlmRequest;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * Intercepts the outgoing {@link LlmRequest} before each model call.
     *
     * <p>Scans {@code contents} for any {@code FunctionResponse} from this tool that still carries
     * raw base64 image data. For each one found it:
     * <ol>
     *   <li>Replaces the response map with a sentinel (base64 stripped) so the conversation history
     *       stays clean on subsequent passes.
     *   <li>Appends a new {@code user}-role {@link Content} containing only the {@code inlineData}
     *       part, placed immediately after the content that held the function response.
     * </ol>
     *
     * <p>Keeping the image in a separate {@code Content} avoids the LangChain4j limitation where
     * {@code ToolExecutionResultMessage} and {@code UserMessage} (which carries {@code ImageContent})
     * are mutually exclusive — the image would otherwise be silently dropped.
     *
     * <p>The method is idempotent: once the sentinel is in place the base64 key is absent and the
     * content is skipped on every subsequent invocation.
     */
    @Override
    public Completable processLlmRequest(
            final LlmRequest.Builder llmRequestBuilder, final ToolContext toolContext) {
        final Completable registration = super.processLlmRequest(llmRequestBuilder, toolContext);

        return registration.doOnComplete(() -> {
            final List<Content> original = llmRequestBuilder.build().contents();
            final List<Content> rewritten = new ArrayList<>();
            boolean anyChanged = false;

            for (final Content content : original) {
                final List<Part> parts = content.parts().orElse(List.of());
                boolean contentChanged = false;
                final List<Part> newParts = new ArrayList<>();
                Content imageContent = null;

                for (final Part part : parts) {
                    final FunctionResponse functionResponse = part.functionResponse().orElse(null);
                    if (functionResponse == null || !Objects.equals(functionResponse.name().orElse(null), name())) {
                        newParts.add(part);
                        continue;
                    }

                    final Map<String, Object> response = functionResponse.response().orElse(Map.of());
                    final String base64Value = CollectionUtils.getStringValueFromMap(response, "base64");
                    if (base64Value == null) {
                        // Already rewritten on a previous pass — leave unchanged.
                        newParts.add(part);
                        continue;
                    }

                    final String mimeType = Objects.requireNonNull(CollectionUtils.getStringValueFromMap(response, "mimeType"));

                    // Strip base64 from the FunctionResponse so history stays clean.
                    final FunctionResponse sentinel = functionResponse.toBuilder()
                            .response(Map.of("status", "Image contents are attached below", "mimeType", mimeType))
                            .build();
                    newParts.add(part.toBuilder().functionResponse(sentinel).build());

                    // Prepare a separate user-role Content carrying only the inlineData.
                    final Part imagePart = Part.fromBytes(Base64.getDecoder().decode(base64Value), mimeType);
                    imageContent = Content.builder()
                            .role("user")
                            .parts(List.of(imagePart))
                            .build();
                    contentChanged = true;
                }

                if (contentChanged) {
                    rewritten.add(content.toBuilder().parts(newParts).build());
                    anyChanged = true;
                } else {
                    rewritten.add(content);
                }

                if (imageContent != null) {
                    rewritten.add(imageContent);
                }
            }

            if (anyChanged) {
                llmRequestBuilder.contents(rewritten);
            }
        });
    }
}
