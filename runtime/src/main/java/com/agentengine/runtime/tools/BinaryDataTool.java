package com.agentengine.runtime.tools;

import com.agentengine.runtime.tools.image.ImageUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.models.LlmRequest;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.InputStream;
import java.util.*;

public class BinaryDataTool extends Tool {

    protected final CloudStorageService cloudStorageService;

    protected BinaryDataTool(final ToolDescriptor toolDescriptor, final boolean isLongRunning, final CloudStorageService cloudStorageService) {
        super(toolDescriptor, isLongRunning);
        this.cloudStorageService = cloudStorageService;
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

        return registration.observeOn(Schedulers.io()).doOnComplete(() -> {
            final List<Content> original = llmRequestBuilder.build().contents();
            final List<Content> rewritten = new ArrayList<>();

            for (final Content content : original) {
                final List<Part> parts = content.parts().orElse(List.of());
                final List<Part> newParts = new ArrayList<>();
                final List<Part> binaryContentParts = new ArrayList<>();

                for (final Part part : parts) {
                    final FunctionResponse functionResponse = part.functionResponse().orElse(null);
                    if (functionResponse == null || !Objects.equals(functionResponse.name().orElse(null), name())) {
                        newParts.add(part);
                        continue;
                    }

                    final Map<String, Object> response = functionResponse.response().orElse(Map.of());
                    final List<Map<String, Object>> files = CollectionUtils.getValueFromMap(response, "files");
                    if (CollectionUtils.isEmpty(files)) {
                        newParts.add(part);
                        continue;
                    }

                    for (final Map<String, Object> file : CollectionUtils.nullSafeList(files)) {
                        final FileDetails raw = JsonUtils.fromMap(file, FileDetails.class);
                        final FileDetails fileDetails = enrichFileDetails(raw);
                        try (final InputStream inputStream = cloudStorageService.download(fileDetails)) {
                            final byte[] bytes = inputStream.readAllBytes();
                            final String mimeType = resolveMimeType(fileDetails, bytes);
                            binaryContentParts.add(Part.fromBytes(bytes, mimeType));
                        }
                    }

                    final Map<String, Object> sentinelData = CollectionUtils.nullSafeMutableMap(response);
                    sentinelData.remove("files");
                    sentinelData.put("message", "File contents are attached in below message");
                    sentinelData.put("fileDetails", JsonUtils.toJson(files));
                    final FunctionResponse sentinel = functionResponse.toBuilder()
                            .response(sentinelData)
                            .build();
                    newParts.add(part.toBuilder().functionResponse(sentinel).build());
                }

                if (CollectionUtils.isNotEmpty(binaryContentParts)) {
                    rewritten.add(content.toBuilder().parts(newParts).build());

                    binaryContentParts.add(Part.fromText("Here are the attached files"));
                    rewritten.add(Content.builder().role("user").parts(binaryContentParts).build());
                } else {
                    rewritten.add(content);
                }
            }
            llmRequestBuilder.contents(rewritten);
        });
    }

    public record FileResponse(List<FileDetails> files){}

    /**
     * Fill in any missing fields on a {@link FileDetails} deserialized from a model response.
     * Models sometimes omit {@code name} or send an incorrect {@code type} — this normalises both.
     */
    private static FileDetails enrichFileDetails(final FileDetails raw) {
        final String source = raw.source() != null ? raw.source() : "";
        String name = raw.name();
        name = StringUtils.isNotBlank(raw.name()) ? name : source.substring(source.lastIndexOf('/') + 1);
        final FileDetails.StorageType type =
                (raw.type() == null || raw.type() == FileDetails.StorageType.UNKNOWN)
                        ? FileDetails.StorageType.CLOUDSTORAGE
                        : raw.type();
        return new FileDetails(name, source, type, raw.mimeType(), raw.size());
    }

    /**
     * Resolve the MIME type for a downloaded file.
     * Priority: explicit mimeType on FileDetails → magic-byte detection → extension fallback.
     */
    private static String resolveMimeType(final FileDetails fileDetails, final byte[] bytes) {
        if (fileDetails.mimeType() != null && !fileDetails.mimeType().isBlank()) {
            return fileDetails.mimeType();
        }
        try {
            return "image/" + ImageUtils.detectFormatFromHeader(bytes);
        } catch (Exception ignored) {
        }
        final String name = fileDetails.name() != null ? fileDetails.name().toLowerCase() : "";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
