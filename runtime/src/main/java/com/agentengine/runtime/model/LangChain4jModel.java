package com.agentengine.runtime.model;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ToolConfig;
import com.google.genai.types.Type;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.*;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ContentType.IMAGE;

/**
 * ADK {@link BaseLlm} implementation backed by LangChain4j's {@link ChatModel} and {@link
 * StreamingChatModel}.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li><b>Streaming responses:</b> {@code partial=true} events are content deltas (text or
 *       thinking tokens). Subsequent {@code partial=false} event contains the fully assembled
 *       response. Tool calls appear only in {@code partial=false}, never in chunks.
 *   <li><b>Thinking in responses:</b> If thinking is present from the model and {@code
 *       returnThinking=true} is set on the underlying {@link StreamingChatModel} or {@link
 *       ChatModel}:
 *       <ul>
 *         <li>Streaming: thinking tokens appear as {@code thought=true, partial=true} events.
 *         <li>Final: thinking appears as {@code thought=true} Part in the {@code partial=false}
 *             response.
 *       </ul>
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Tool specifications and schemas must be well-formed (declarations and parameter schemas
 *       present). Missing declarations/schemas cause {@code IllegalStateException}.
 * </ul>
 *
 * <h3>OpenAI-compatible API constraint</h3>
 *
 * <p>When converting session history to API messages, text is dropped from model turns that also
 * contain tool calls. OpenAI-compatible APIs (e.g. llama.cpp) reject {@code AssistantMessage} with
 * both {@code content} and {@code tool_calls}. The text is preserved in the session {@code Content}
 * parts for observability and future model compatibility.
 */
public final class LangChain4jModel extends BaseLlm {
    private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jModel.class);

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public LangChain4jModel(
            final ChatModel chatModel, final StreamingChatModel streamingChatModel, final String modelName) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    @Override
    public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
        if (stream) {
            if (streamingChatModel == null) {
                return Flowable.error(new IllegalStateException("StreamingChatModel is not configured"));
            }
            return Flowable.create(
                    emitter -> streamingChatModel.chat(toChatRequest(llmRequest), new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(final String token) {
                            emitter.onNext(LlmResponse.builder()
                                    .content(Content.builder()
                                            .role("model")
                                            .parts(Part.fromText(token))
                                            .build())
                                    .partial(true)
                                    .build());
                        }

                        @Override
                        public void onPartialThinking(final PartialThinking partialThinking) {
                            emitter.onNext(LlmResponse.builder()
                                    .content(Content.builder()
                                            .role("model")
                                            .parts(Part.builder()
                                                    .text(partialThinking.text())
                                                    .thought(true)
                                                    .build())
                                            .build())
                                    .partial(true)
                                    .build());
                        }

                        @Override
                        public void onCompleteResponse(final ChatResponse chatResponse) {
                            final List<Part> parts = toParts(chatResponse.aiMessage());
                            if (CollectionUtils.isNotEmpty(parts)) {
                                emitter.onNext(finalResponse(parts));
                            }
                            emitter.onComplete();
                        }

                        @Override
                        public void onError(final Throwable throwable) {
                            emitter.onError(throwable);
                        }
                    }),
                    BackpressureStrategy.BUFFER);
        } else {
            if (chatModel == null) {
                return Flowable.error(new IllegalStateException("ChatModel is not configured"));
            }
            return Flowable.just(finalResponse(
                    toParts(chatModel.chat(toChatRequest(llmRequest)).aiMessage())));
        }
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
        throw new UnsupportedOperationException("Live connection is not supported.");
    }

    private static LlmResponse finalResponse(final List<Part> parts) {
        return LlmResponse.builder()
                .content(Content.builder().role("model").parts(parts).build())
                .partial(false)
                .build();
    }

    private static ChatRequest toChatRequest(final LlmRequest llmRequest) {
        final ChatRequest.Builder builder = ChatRequest.builder();
        final List<ToolSpecification> toolSpecifications = toToolSpecifications(llmRequest);
        builder.toolSpecifications(toolSpecifications);
        llmRequest.config().ifPresent(config -> applyConfig(builder, config, toolSpecifications));
        final ChatRequest chatRequest = builder.messages(toMessages(llmRequest)).build();
        LOGGER.info("Converted llmRequest : {}", JsonUtils.toJson(llmRequest));
        return chatRequest;
    }

    private static void applyConfig(
            final ChatRequest.Builder builder,
            final GenerateContentConfig config,
            final List<ToolSpecification> toolSpecifications) {
        config.temperature().ifPresent(temperature -> builder.temperature(temperature.doubleValue()));
        config.topP().ifPresent(topP -> builder.topP(topP.doubleValue()));
        config.topK().ifPresent(topK -> builder.topK(topK.intValue()));
        config.maxOutputTokens().ifPresent(builder::maxOutputTokens);
        config.stopSequences().ifPresent(builder::stopSequences);
        config.frequencyPenalty()
                .ifPresent(frequencyPenalty -> builder.frequencyPenalty(frequencyPenalty.doubleValue()));
        config.presencePenalty().ifPresent(presencePenalty -> builder.presencePenalty(presencePenalty.doubleValue()));
        config.toolConfig()
                .ifPresent(toolConfiguration -> applyToolConfig(builder, toolConfiguration, toolSpecifications));
    }

    private static void applyToolConfig(
            final ChatRequest.Builder builder,
            final ToolConfig toolConfig,
            final List<ToolSpecification> toolSpecifications) {
        toolConfig
                .functionCallingConfig()
                .ifPresent(functionCallingConfig -> functionCallingConfig.mode().ifPresent(mode -> {
                    if (FunctionCallingConfigMode.Known.AUTO.equals(mode.knownEnum())) {
                        builder.toolChoice(ToolChoice.AUTO);
                    } else if (FunctionCallingConfigMode.Known.ANY.equals(mode.knownEnum())) {
                        builder.toolChoice(ToolChoice.REQUIRED);
                        functionCallingConfig
                                .allowedFunctionNames()
                                .ifPresent(
                                        allowedFunctionNames -> builder.toolSpecifications(toolSpecifications.stream()
                                                .filter(toolSpecification ->
                                                        allowedFunctionNames.contains(toolSpecification.name()))
                                                .toList()));
                    } else if (FunctionCallingConfigMode.Known.NONE.equals(mode.knownEnum())) {
                        builder.toolSpecifications(List.of());
                    }
                }));
    }

    private static List<ChatMessage> toMessages(final LlmRequest llmRequest) {
        final List<ChatMessage> messages = new ArrayList<>(llmRequest.getSystemInstructions().stream()
                .map(SystemMessage::from)
                .toList());
        llmRequest.contents().forEach(content -> messages.addAll(toChatMessage(content)));
        return messages;
    }

    private static List<ChatMessage> toChatMessage(final Content content) {
        return switch (content.role().orElseThrow().toLowerCase()) {
            case Constants.AUTHOR_USER -> toUserOrToolResultMessages(content);
            case "model", "assistant" -> List.of(toAiMessage(content));
            default -> throw new IllegalStateException("Unexpected role: " + content.role());
        };
    }

    private static List<ChatMessage> toUserOrToolResultMessages(final Content content) {
        final List<Part> parts = content.parts().orElse(List.of());

        // ADK encodes tool results as user-role Content with FunctionResponse parts.
        // When present, each becomes a separate ToolExecutionResultMessage; otherwise
        // it is a UserMessage.
        final List<ChatMessage> toolResults = parts.stream()
                .filter(part -> part.functionResponse().isPresent())
                .map(part -> {
                    final FunctionResponse functionResponse =
                            part.functionResponse().get();
                    return (ChatMessage) ToolExecutionResultMessage.from(
                            functionResponse.id().orElseThrow(),
                            functionResponse.name().orElseThrow(),
                            JsonUtils.toJson(functionResponse.response().orElseThrow()));
                })
                .toList();
        if (!toolResults.isEmpty()) {
            return toolResults;
        }

        return List.of(UserMessage.from(
                parts.stream().map(LangChain4jModel::toUserContent).toList()));
    }

    private static dev.langchain4j.data.message.Content toUserContent(final Part part) {
        if (part.text().isPresent()) {
            return TextContent.from(part.text().get());
        }
        if (part.inlineData().isPresent()) {
            return toMediaContent(part.inlineData().get());
        }
        throw new IllegalStateException("Unexpected part in user content: " + part);
    }

    private static dev.langchain4j.data.message.Content toMediaContent(final Blob blob) {
        final String mimeType = blob.mimeType().orElseThrow(() -> new IllegalArgumentException("Mime type required"));
        final byte[] bytes = blob.data().orElseThrow(() -> new IllegalArgumentException("Data required"));
        final String base64 = Base64.getEncoder().encodeToString(bytes);
        final String name = blob.displayName().orElse("");
        if (mimeType.startsWith("audio/") || name.endsWith(".mp3")) {
            return AudioContent.from(
                    Audio.builder().base64Data(base64).mimeType(mimeType).build());
        }
        if (mimeType.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) {
            return VideoContent.from(
                    Video.builder().base64Data(base64).mimeType(mimeType).build());
        }
        if (mimeType.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
            return ImageContent.from(
                    Image.builder().base64Data(base64).mimeType(mimeType).build());
        }

        if (mimeType.startsWith("application/pdf") || name.endsWith(".pdf")) {
            return PdfFileContent.from(
                    PdfFile.builder().base64Data(base64).mimeType(mimeType).build());
        }
        return TextContent.from(new String(bytes, StandardCharsets.UTF_8));
    }

    private static AiMessage toAiMessage(final Content content) {
        final List<String> texts = new ArrayList<>();
        final List<ToolExecutionRequest> toolCalls = new ArrayList<>();
        content.parts().orElse(List.of()).forEach(part -> {
            if (part.text().isPresent()) {
                texts.add(part.text().get());
            } else if (part.functionCall().isPresent()) {
                final FunctionCall fc = part.functionCall().get();
                toolCalls.add(ToolExecutionRequest.builder()
                        .id(fc.id().orElseThrow())
                        .name(fc.name().orElseThrow())
                        .arguments(JsonUtils.toJson(fc.args().orElseThrow()))
                        .build());
            } else {
                throw new IllegalStateException("Either text or functionCall expected, but was: " + part);
            }
        });

        if (!toolCalls.isEmpty()) {
            // Drop text when tool calls are present: OpenAI-compatible APIs reject
            // AssistantMessage with
            // both content and tool_calls. Text is preserved in session Content parts for
            // observability.
            return AiMessage.builder().toolExecutionRequests(toolCalls).build();
        }
        return AiMessage.builder().text(String.join("\n", texts)).build();
    }

    // Ordered: thinking → text → tool calls, matching natural model output order.
    private static List<Part> toParts(final AiMessage aiMessage) {
        final List<Part> parts = new ArrayList<>();
        final String thinking = aiMessage.thinking();
        if (StringUtils.isNotBlank(thinking)) {
            parts.add(Part.builder().text(thinking).thought(true).build());
        }
        final String text = aiMessage.text();
        if (StringUtils.isNotBlank(text)) {
            parts.add(Part.fromText(text));
        }
        if (aiMessage.hasToolExecutionRequests()) {
            for (final ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                parts.add(Part.builder()
                        .functionCall(FunctionCall.builder()
                                .id(
                                        request.id() != null
                                                ? request.id()
                                                : UUID.randomUUID().toString())
                                .name(request.name())
                                .args(CollectionUtils.nullSafeMap(
                                        JsonUtils.fromJson(request.arguments(), new TypeReference<>() {})))
                                .build())
                        .build());
            }
        }
        return parts;
    }

    private static List<ToolSpecification> toToolSpecifications(final LlmRequest llmRequest) {
        return llmRequest.tools().values().stream()
                .map(tool -> {
                    final FunctionDeclaration decl = tool.declaration()
                            .orElseThrow(() -> new IllegalStateException("Tool lacking declaration: " + tool));
                    final Schema schema = decl.parameters()
                            .orElseThrow(() -> new IllegalStateException("Tool lacking parameters: " + tool));
                    return ToolSpecification.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .parameters(toParameters(schema))
                            .build();
                })
                .toList();
    }

    private static JsonObjectSchema toParameters(final Schema schema) {
        if (schema.type().isPresent()
                && Type.Known.OBJECT.equals(schema.type().get().knownEnum())) {
            final Map<String, JsonSchemaElement> properties = new HashMap<>();
            schema.properties()
                    .orElse(Map.of())
                    .forEach((propertyName, propertySchema) ->
                            properties.put(propertyName, toSchemaElement(propertySchema)));
            return JsonObjectSchema.builder()
                    .addProperties(properties)
                    .required(schema.required().orElse(List.of()))
                    .build();
        }
        throw new UnsupportedOperationException("Unsupported schema type: " + schema.type());
    }

    private static JsonSchemaElement toSchemaElement(final Schema schema) {
        if (schema == null || schema.type().isEmpty()) {
            throw new IllegalArgumentException("Schema type cannot be null or absent");
        }
        final String description = schema.description().orElse(null);
        return switch (schema.type().get().knownEnum()) {
            case STRING -> JsonStringSchema.builder().description(description).build();
            case NUMBER -> JsonNumberSchema.builder().description(description).build();
            case INTEGER -> JsonIntegerSchema.builder().description(description).build();
            case BOOLEAN -> JsonBooleanSchema.builder().description(description).build();
            case ARRAY ->
                JsonArraySchema.builder()
                        .description(description)
                        .items(toSchemaElement(schema.items().orElseThrow()))
                        .build();
            case OBJECT -> toParameters(schema);
            default ->
                throw new UnsupportedFeatureException(
                        "Unsupported schema type: " + schema.type().get());
        };
    }
}
