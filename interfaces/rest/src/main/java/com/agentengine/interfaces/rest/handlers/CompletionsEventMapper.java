package com.agentengine.interfaces.rest.handlers;

import com.agentengine.interfaces.rest.dto.responses.ChatCompletionChunk;
import com.agentengine.interfaces.rest.dto.responses.ChunkChoice;
import com.agentengine.interfaces.rest.dto.responses.CompletionUsage;
import com.agentengine.interfaces.rest.dto.responses.MessageDelta;
import com.agentengine.interfaces.rest.handlers.ResponsesEventMapper.ResponseOutputEvent;
import com.agui.community.core.event.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.List;

/**
 * Maps AG-UI events to OpenAI Chat Completions API chunks. Wraps ResponsesEventMapper to convert
 * ResponseOutputEvent to ChatCompletionChunk format.
 */
public final class CompletionsEventMapper {

    private final String completionId;
    private final String modelId;
    private final long created;
    private final ResponsesEventMapper responsesMapper;

    public CompletionsEventMapper(String completionId, String modelId) {
        this.completionId = completionId;
        this.modelId = modelId;
        this.created = Instant.now().getEpochSecond();
        this.responsesMapper = new ResponsesEventMapper(completionId, modelId, created);
    }

    /**
     * Map an AG-UI event to a Flowable of ChatCompletionChunk objects. RESTEasy Reactive will handle
     * JSON serialization and SSE formatting.
     */
    public Flowable<ChatCompletionChunk> mapEvent(Event event) {
        return responsesMapper.mapEvent(event).map(this::toChatCompletionChunk);
    }

    /**
     * Convert ResponseOutputEvent to ChatCompletionChunk format. Only emits chunks for
     * content/reasoning deltas, ignores structural events.
     */
    private ChatCompletionChunk toChatCompletionChunk(ResponseOutputEvent outputEvent) {
        String content = null;
        String reasoning = null;
        String finishReason = outputEvent.finishReason();

        // Map Responses API event types to Chat Completions delta format
        if ("response.output_text.delta".equals(outputEvent.type())) {
            content = outputEvent.delta();
        } else if ("response.reasoning.delta".equals(outputEvent.type())) {
            reasoning = outputEvent.delta();
        } else if ("response.completed".equals(outputEvent.type())) {
            finishReason = outputEvent.finishReason();
        }

        MessageDelta delta = new MessageDelta("assistant", content, null, null, null, reasoning);
        ChunkChoice choice = new ChunkChoice(0, delta, finishReason, null);
        CompletionUsage usage = outputEvent.usage() != null
                ? new CompletionUsage(
                        outputEvent.usage().promptTokens(),
                        outputEvent.usage().completionTokens(),
                        outputEvent.usage().totalTokens())
                : null;
        return new ChatCompletionChunk(
                completionId, "chat.completion.chunk", created, modelId, List.of(choice), null, null, usage);
    }
}
