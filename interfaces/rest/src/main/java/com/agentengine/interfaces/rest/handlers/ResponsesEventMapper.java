package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.agui.ReasoningEndEvent;
import com.agentengine.engine.api.agui.ReasoningMessageContentEvent;
import com.agentengine.engine.api.agui.ReasoningMessageEndEvent;
import com.agentengine.engine.api.agui.ReasoningMessageStartEvent;
import com.agentengine.engine.api.agui.ReasoningStartEvent;
import com.agui.core.event.*;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Maps AG-UI events to OpenAI Responses API output events.
 * 
 * <p>
 * The Responses API uses a different SSE event format than Chat Completions:
 * 
 * <pre>
 * event: response.output_item.added
 * data: {"type":"response.output_item.added","output_index":0,"item":{...}}
 * 
 * event: response.output_text.delta
 * data: {"type":"response.output_text.delta","delta":"Hello"}
 * 
 * event: response.completed
 * data: {"type":"response.completed","response":{...}}
 * </pre>
 */
public final class ResponsesEventMapper {

  private final String responseId;
  private final String modelId;
  private final long createdAt;
  private int outputIndex;

  public ResponsesEventMapper(String responseId, String modelId, long createdAt) {
    this.responseId = responseId;
    this.modelId = modelId;
    this.createdAt = createdAt;
    this.outputIndex = 0;
  }

  /**
   * Map an AG-UI event to a Flowable of ResponseOutputEvent objects. RESTEasy
   * Reactive will handle JSON serialization and SSE formatting.
   */
  public Flowable<ResponseOutputEvent> mapEvent(BaseEvent event) {
    if (event == null) {
      return Flowable.empty();
    }

    // Text message events
    if (event instanceof TextMessageChunkEvent chunk) {
      String delta = chunk.getDelta();
      if (delta == null || delta.isEmpty()) {
        return Flowable.empty();
      }
      return contentDelta(delta);
    }

    if (event instanceof TextMessageContentEvent content) {
      String delta = content.getDelta();
      if (delta == null || delta.isEmpty()) {
        return Flowable.empty();
      }
      return contentDelta(delta);
    }

    if (event instanceof TextMessageStartEvent) {
      return outputItemAdded("message");
    }

    if (event instanceof TextMessageEndEvent) {
      return outputItemDone("message");
    }

    // Reasoning events
    if (event instanceof ReasoningMessageContentEvent reasoning) {
      String delta = reasoning.getDelta();
      if (delta == null || delta.isEmpty()) {
        return Flowable.empty();
      }
      return reasoningDelta(delta);
    }

    if (event instanceof ReasoningMessageStartEvent) {
      return outputItemAdded("reasoning");
    }

    if (event instanceof ReasoningMessageEndEvent) {
      return outputItemDone("reasoning");
    }

    if (event instanceof ReasoningStartEvent) {
      return Flowable.just(new ResponseOutputEvent("response.reasoning.started", null, null, null));
    }

    if (event instanceof ReasoningEndEvent) {
      return Flowable.just(new ResponseOutputEvent("response.reasoning.done", null, null, null));
    }

    // Tool call events
    if (event instanceof ToolCallStartEvent toolStart) {
      return toolCallAdded(toolStart.getToolCallId(), toolStart.getToolCallName());
    }

    if (event instanceof ToolCallArgsEvent toolArgs) {
      return toolCallArgumentsDelta(toolArgs.getToolCallId(), toolArgs.getDelta());
    }

    if (event instanceof ToolCallEndEvent) {
      return toolCallDone();
    }

    if (event instanceof ToolCallResultEvent toolResult) {
      return toolResultAdded(toolResult.getToolCallId(), toolResult.getContent());
    }

    // Run lifecycle events
    if (event instanceof RunFinishedEvent) {
      return complete();
    }

    if (event instanceof RunErrorEvent error) {
      return error(error.getError());
    }

    return Flowable.empty();
  }

  /**
   * Emit a content delta event.
   */
  private Flowable<ResponseOutputEvent> contentDelta(String delta) {
    return Flowable.just(new ResponseOutputEvent("response.output_text.delta", delta, null, null));
  }

  /**
   * Emit a reasoning delta event.
   */
  private Flowable<ResponseOutputEvent> reasoningDelta(String delta) {
    return Flowable.just(new ResponseOutputEvent("response.reasoning.delta", delta, null, null));
  }

  /**
   * Emit output_item.added event.
   */
  private Flowable<ResponseOutputEvent> outputItemAdded(String itemType) {
    int index = outputIndex++;
    return Flowable.just(new ResponseOutputEvent("response.output_item.added", null, null, null, index, itemType));
  }

  /**
   * Emit output_item.done event.
   */
  private Flowable<ResponseOutputEvent> outputItemDone(String itemType) {
    return Flowable.just(new ResponseOutputEvent("response.output_item.done", null, null, null, outputIndex - 1, itemType));
  }

  /**
   * Emit function_call.output_item.added event.
   */
  private Flowable<ResponseOutputEvent> toolCallAdded(String toolCallId, String toolName) {
    int index = outputIndex++;
    return Flowable.just(new ResponseOutputEvent("response.function_call.output_item.added", null, null, null, index, "function_call",
        toolCallId, toolName));
  }

  /**
   * Emit function_call.arguments.delta event.
   */
  private Flowable<ResponseOutputEvent> toolCallArgumentsDelta(String toolCallId, String arguments) {
    return Flowable
        .just(new ResponseOutputEvent("response.function_call.arguments.delta", arguments, null, null, null, null, toolCallId, null));
  }

  /**
   * Emit function_call.done event.
   */
  private Flowable<ResponseOutputEvent> toolCallDone() {
    return Flowable.just(new ResponseOutputEvent("response.function_call.done", null, null, null));
  }

  /**
   * Emit tool_call.output_item.added event.
   */
  private Flowable<ResponseOutputEvent> toolResultAdded(String toolCallId, String result) {
    int index = outputIndex++;
    return Flowable
        .just(new ResponseOutputEvent("response.tool_call.output_item.added", result, null, null, index, "tool_call", toolCallId, null));
  }

  /**
   * Emit completion event with usage.
   */
  private Flowable<ResponseOutputEvent> complete() {
    Usage usage = new Usage(0, 0, 0);
    return Flowable.just(new ResponseOutputEvent("response.completed", null, "stop", usage));
  }

  /**
   * Emit error event.
   */
  private Flowable<ResponseOutputEvent> error(String message) {
    return Flowable.error(new RuntimeException(message));
  }

  /**
   * Response output event for the Responses API.
   *
   * @param type
   *          The event type (e.g., "response.output_text.delta",
   *          "response.completed")
   * @param delta
   *          The content delta (for delta events)
   * @param finishReason
   *          The finish reason (for completion events)
   * @param usage
   *          Token usage (for completion events)
   * @param outputIndex
   *          The output index (for output_item events)
   * @param itemType
   *          The item type (for output_item events)
   * @param toolCallId
   *          The tool call ID (for tool call events)
   * @param toolName
   *          The tool name (for tool call added events)
   */
  public record ResponseOutputEvent(String type, String delta, String finishReason, Usage usage, Integer outputIndex, String itemType,
      String toolCallId, String toolName) {
    // Constructor for simple events (no output_index, item_type, tool_call_id,
    // tool_name)
    public ResponseOutputEvent(String type, String delta, String finishReason, Usage usage) {
      this(type, delta, finishReason, usage, null, null, null, null);
    }

    // Constructor for output_item events
    public ResponseOutputEvent(String type, String delta, String finishReason, Usage usage, Integer outputIndex, String itemType) {
      this(type, delta, finishReason, usage, outputIndex, itemType, null, null);
    }
  }

  /**
   * Token usage information.
   */
  public record Usage(int promptTokens, int completionTokens, int totalTokens) {
  }
}
