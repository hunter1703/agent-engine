package com.agentengine.interfaces.rest.responses;

import com.agui.core.event.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps AGUI events to Responses API format for Codex CLI compatibility
 */
@ApplicationScoped
public class ResponsesApiMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // Track ongoing operations for proper event sequencing
  private final Map<String, Object> ongoingOperations = new HashMap<>();
  // Track which tool calls are update_plan calls
  private final Map<String, Boolean> updatePlanCalls = new HashMap<>();
  // Track accumulated arguments for tool calls
  private final Map<String, StringBuilder> toolCallArguments = new HashMap<>();

  public ResponsesApiEvent mapEvent(BaseEvent baseEvent) {
    if (baseEvent instanceof RunStartedEvent) {
      return createResponseCreatedEvent((RunStartedEvent) baseEvent);
    } else if (baseEvent instanceof RunFinishedEvent) {
      return createResponseCompletedEvent((RunFinishedEvent) baseEvent);
    } else if (baseEvent instanceof RunErrorEvent) {
      return createResponseFailedEvent((RunErrorEvent) baseEvent);
    } else if (baseEvent instanceof ThinkingStartEvent) {
      return createReasoningAddedEvent((ThinkingStartEvent) baseEvent);
    } else if (baseEvent instanceof ThinkingEndEvent) {
      return createReasoningDoneEvent((ThinkingEndEvent) baseEvent);
    } else if (baseEvent instanceof final ToolCallStartEvent toolStartEvent) {
      if ("update_plan".equals(toolStartEvent.getToolCallName())) {
        return createUpdatePlanAddedEvent(toolStartEvent);
      }
      return createToolCallAddedEvent(toolStartEvent);
    } else if (baseEvent instanceof final ToolCallEndEvent toolEndEvent) {
      if (isUpdatePlanCall(toolEndEvent.getToolCallId())) {
        return createUpdatePlanDoneEvent(toolEndEvent);
      }
      return createToolCallDoneEvent(toolEndEvent);
    } else if (baseEvent instanceof final ToolCallArgsEvent argsEvent) {
      if (isUpdatePlanCall(argsEvent.getToolCallId())) {
        return createUpdatePlanArgsEvent(argsEvent);
      }
      return createToolCallArgsEvent(argsEvent);
    } else if (baseEvent instanceof ToolCallResultEvent) {
      return createToolCallResultEvent((ToolCallResultEvent) baseEvent);
    } else if (baseEvent instanceof TextMessageStartEvent) {
      return createTextMessageAddedEvent((TextMessageStartEvent) baseEvent);
    } else if (baseEvent instanceof TextMessageEndEvent) {
      return createTextMessageDoneEvent((TextMessageEndEvent) baseEvent);
    } else if (baseEvent instanceof TextMessageChunkEvent) {
      return createTextDeltaEvent((TextMessageChunkEvent) baseEvent);
    } else if (baseEvent instanceof StepStartedEvent) {
      return createResponseInProgressEvent((StepStartedEvent) baseEvent);
    }

    return new ResponsesApiEvent("response.in_progress", "{}");
  }

  private ResponsesApiEvent createResponseCreatedEvent(RunStartedEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", STR."resp_\{UUID.randomUUID().toString().replace("-", "")}");
        data.put("status", "created");
        data.put("object", "response");
        
        return new ResponsesApiEvent("response.created", toJson(data));
    }

  private ResponsesApiEvent createResponseCompletedEvent(RunFinishedEvent event) {
    Map<String, Object> data = new HashMap<>();
    data.put("status", "completed");
    data.put("usage", Map.of("input_tokens", 0, "output_tokens", 0));

    return new ResponsesApiEvent("response.completed", toJson(data));
  }

  private ResponsesApiEvent createResponseFailedEvent(RunErrorEvent event) {
    Map<String, Object> data = new HashMap<>();
    data.put("error", Map.of("type", "response_error", "message", event.getError()));

    return new ResponsesApiEvent("response.failed", toJson(data));
  }

  private ResponsesApiEvent createReasoningAddedEvent(ThinkingStartEvent event) {
    String itemId = "rs_" + UUID.randomUUID().toString().replace("-", "");

    Map<String, Object> item = new HashMap<>();
    item.put("id", itemId);
    item.put("type", "reasoning");
    item.put("summary", new Object[]{});

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", 0);
    data.put("item", item);

    // Store for potential updates
    ongoingOperations.put(itemId, item);

    return new ResponsesApiEvent("response.output_item.added", toJson(data));
  }

  private ResponsesApiEvent createReasoningDoneEvent(ThinkingEndEvent event) {
    // For now, return an empty done event for reasoning
    // In a real implementation, we'd have the final reasoning text
    Map<String, Object> data = new HashMap<>();
    data.put("output_index", 0);
    data.put("item", Map.of("id", "placeholder_reasoning_id", "type", "reasoning", "summary",
        new Object[]{Map.of("type", "summary_text", "text", "Analysis complete")}));

    return new ResponsesApiEvent("response.output_item.done", toJson(data));
  }

  private ResponsesApiEvent createToolCallAddedEvent(ToolCallStartEvent event) {
    String callId = event.getToolCallId();
    String toolName = event.getToolCallName();

    // Initialize the arguments accumulator
    toolCallArguments.put(callId, new StringBuilder());

    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("call_id", callId);
    item.put("name", toolName);
    item.put("arguments", ""); // Will be populated by arguments event

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getNextOutputIndex());
    data.put("item", item);

    return new ResponsesApiEvent("response.output_item.added", toJson(data));
  }

  private ResponsesApiEvent createToolCallArgsEvent(ToolCallArgsEvent event) {
    String callId = event.getToolCallId();

    // Accumulate the arguments for this tool call
    StringBuilder sb = toolCallArguments.computeIfAbsent(callId, k -> new StringBuilder());
    sb.append(event.getDelta());

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getLastOutputIndex());
    data.put("call_id", callId);
    data.put("delta", event.getDelta());

    return new ResponsesApiEvent("response.function_call_arguments.delta", toJson(data));
  }

  private ResponsesApiEvent createToolCallDoneEvent(ToolCallEndEvent event) {
    String callId = event.getToolCallId();

    // Get the accumulated arguments
    StringBuilder sb = toolCallArguments.get(callId);
    String arguments = sb != null ? sb.toString() : "{}";

    // Need to reconstruct the full item for the done event
    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("call_id", callId);
    item.put("name", "unknown"); // Would need to store this from start event
    item.put("arguments", arguments);

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getLastOutputIndex());
    data.put("item", item);

    // Clean up the arguments tracker
    toolCallArguments.remove(callId);

    return new ResponsesApiEvent("response.output_item.done", toJson(data));
  }

  private ResponsesApiEvent createToolCallResultEvent(ToolCallResultEvent event) {
    String callId = event.getToolCallId();

    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call_output");
    item.put("call_id", callId);
    item.put("output", event.getContent());

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getNextOutputIndex());
    data.put("item", item);

    return new ResponsesApiEvent("response.output_item.added", toJson(data));
  }

  private ResponsesApiEvent createTextMessageAddedEvent(TextMessageStartEvent event) {
    String messageId = event.getMessageId();

    Map<String, Object> item = new HashMap<>();
    item.put("id", messageId);
    item.put("type", "text");
    item.put("text", "");

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getNextOutputIndex());
    data.put("item", item);

    return new ResponsesApiEvent("response.output_item.added", toJson(data));
  }

  private ResponsesApiEvent createTextDeltaEvent(TextMessageChunkEvent event) {
    String itemId = event.getMessageId();

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getLastOutputIndex());
    data.put("item_id", itemId);
    data.put("delta", event.getDelta());

    return new ResponsesApiEvent("response.output_text.delta", toJson(data));
  }

  private ResponsesApiEvent createTextMessageDoneEvent(TextMessageEndEvent event) {
    String itemId = event.getMessageId();

    Map<String, Object> item = new HashMap<>();
    item.put("id", itemId);
    item.put("type", "text");
    item.put("text", "final text would be accumulated here");

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getLastOutputIndex());
    data.put("item", item);

    return new ResponsesApiEvent("response.output_item.done", toJson(data));
  }

  private ResponsesApiEvent createResponseInProgressEvent(StepStartedEvent event) {
    Map<String, Object> data = new HashMap<>();
    data.put("status", "in_progress");

    return new ResponsesApiEvent("response.in_progress", toJson(data));
  }

  private String toJson(Object obj) {
    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      Log.error("Error serializing event data to JSON", e);
      return "{}";
    }
  }

  private int getNextOutputIndex() {
    // In a real implementation, this would track the sequence properly
    return 0;
  }

  private int getLastOutputIndex() {
    // In a real implementation, this would return the most recent index
    return 0;
  }

  private boolean isUpdatePlanCall(String callId) {
    return updatePlanCalls.getOrDefault(callId, false);
  }

  private ResponsesApiEvent createUpdatePlanAddedEvent(ToolCallStartEvent event) {
    String callId = event.getToolCallId();

    updatePlanCalls.put(callId, true);
    toolCallArguments.put(callId, new StringBuilder());

    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("call_id", callId);
    item.put("name", "update_plan");
    item.put("arguments", "");

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getNextOutputIndex());
    data.put("item", item);

    return new ResponsesApiEvent("response.output_item.added", toJson(data));
  }

  private ResponsesApiEvent createUpdatePlanArgsEvent(ToolCallArgsEvent event) {
    String callId = event.getToolCallId();

    StringBuilder sb = toolCallArguments.computeIfAbsent(callId, _ -> new StringBuilder());
    sb.append(event.getDelta());

    String delta = event.getDelta();
    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getLastOutputIndex());
    data.put("call_id", callId);
    data.put("delta", delta);

    return new ResponsesApiEvent("response.function_call_arguments.delta", toJson(data));
  }

  private ResponsesApiEvent createUpdatePlanDoneEvent(ToolCallEndEvent event) {
    String callId = event.getToolCallId();

    StringBuilder sb = toolCallArguments.get(callId);
    String arguments = sb != null ? sb.toString() : "{}";

    // For update_plan, we need to ensure it's properly formatted for Codex CLI
    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("call_id", callId);
    item.put("name", "update_plan");
    item.put("arguments", arguments);

    Map<String, Object> data = new HashMap<>();
    data.put("output_index", getLastOutputIndex());
    data.put("item", item);

    // Clean up the tracking maps
    updatePlanCalls.remove(callId);
    toolCallArguments.remove(callId);

    return new ResponsesApiEvent("response.output_item.done", toJson(data));
  }
}