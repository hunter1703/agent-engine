package com.agentengine.interfaces.rest.responses;

import com.agui.core.event.*;
import com.agentengine.interfaces.rest.responses.dtos.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Maps AGUI events to Responses API format for Codex CLI compatibility
 */
@ApplicationScoped
public class ResponsesApiMapper {

  // Track ongoing operations for proper event sequencing
  private final Map<String, Object> ongoingOperations = new ConcurrentHashMap<>();
  // Track which tool calls are update_plan calls
  private final Map<String, Boolean> updatePlanCalls = new ConcurrentHashMap<>();
  // Track accumulated arguments for tool calls
  private final Map<String, StringBuilder> toolCallArguments = new ConcurrentHashMap<>();
  // Track tool call names for proper completion events
  private final Map<String, String> toolCallNames = new ConcurrentHashMap<>();
  // Track output indices for proper sequencing
  private final Map<String, Integer> outputIndices = new ConcurrentHashMap<>();
  // Track content indices for reasoning
  private final Map<String, Integer> contentIndices = new ConcurrentHashMap<>();
  // Track summary indices for reasoning summaries
  private final Map<String, Integer> summaryIndices = new ConcurrentHashMap<>();

  private volatile int globalOutputIndex = 0;
  private volatile int globalContentIndex = 0;
  private volatile int globalSummaryIndex = 0;

  public BaseEventData mapEvent(BaseEvent baseEvent, String model) {
    if (baseEvent instanceof RunStartedEvent) {
      return createResponseCreatedEvent(model);
    } else if (baseEvent instanceof RunFinishedEvent) {
      // After completed, we should also send the done event to signal stream
      // completion
      // For now, returning just completed event - the caller should handle sending
      // done
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
    } else if (baseEvent instanceof TextMessageContentEvent) {
      return createTextContentEvent((TextMessageContentEvent) baseEvent);
    } else if (baseEvent instanceof StepStartedEvent) {
      return createResponseInProgressEvent((StepStartedEvent) baseEvent);
    }

    Map<String, Object> data = new HashMap<>();
    data.put("status", "in_progress");
    return new ResponseInProgressEventData(data);
  }

  private ResponseCreatedEventData createResponseCreatedEvent(final String model) {
    String id = "resp_" + UUID.randomUUID().toString().replace("-", "");
    long created = System.currentTimeMillis() / 1000; // Unix timestamp

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("id", id);
    responseData.put("model", model);
    responseData.put("status", "in_progress"); // According to schema, should be in_progress initially
    responseData.put("object", "response");
    responseData.put("created", created);

    return new ResponseCreatedEventData(responseData);
  }

  private ResponseCompletedEventData createResponseCompletedEvent(RunFinishedEvent event) {
    String id = "resp_" + UUID.randomUUID().toString().replace("-", "");

    Map<String, Object> usageData = new HashMap<>();
    usageData.put("total_tokens", 0);
    usageData.put("output_tokens", 0);
    usageData.put("input_tokens", 0);

    // Add input_tokens_details and output_tokens_details if needed
    Map<String, Object> inputTokensDetails = new HashMap<>();
    inputTokensDetails.put("cached_tokens", 0);
    usageData.put("input_tokens_details", inputTokensDetails);

    Map<String, Object> outputTokensDetails = new HashMap<>();
    outputTokensDetails.put("reasoning_tokens", 0);
    usageData.put("output_tokens_details", outputTokensDetails);

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("id", id);
    responseData.put("usage", usageData);
    responseData.put("output", new ArrayList<>()); // Empty output array for now

    return new ResponseCompletedEventData(responseData);
  }

  private ResponseDoneEventData createResponseDoneEvent() {
    String id = "resp_" + UUID.randomUUID().toString().replace("-", "");

    Map<String, Object> usageData = new HashMap<>();
    usageData.put("total_tokens", 0);
    usageData.put("output_tokens", 0);
    usageData.put("input_tokens", 0);

    // Add input_tokens_details and output_tokens_details if needed
    Map<String, Object> inputTokensDetails = new HashMap<>();
    inputTokensDetails.put("cached_tokens", 0);
    usageData.put("input_tokens_details", inputTokensDetails);

    Map<String, Object> outputTokensDetails = new HashMap<>();
    outputTokensDetails.put("reasoning_tokens", 0);
    usageData.put("output_tokens_details", outputTokensDetails);

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("id", id);
    responseData.put("status", "completed");
    responseData.put("object", "response");
    responseData.put("usage", usageData);

    return new ResponseDoneEventData(responseData);
  }

  private ResponseFailedEventData createResponseFailedEvent(RunErrorEvent event) {
    String id = "resp_" + UUID.randomUUID().toString().replace("-", "");
    long createdAt = System.currentTimeMillis() / 1000; // Unix timestamp

    Map<String, Object> errorData = new HashMap<>();
    errorData.put("type", "error");
    errorData.put("code", "internal_error"); // Default error code
    errorData.put("message", event.getError()); // Use actual error message (assuming event.getError() returns the
                                                // message)

    Map<String, Object> responseData = new HashMap<>();
    responseData.put("id", id);
    responseData.put("object", "response");
    responseData.put("created_at", createdAt);
    responseData.put("status", "failed");
    responseData.put("background", false);
    responseData.put("error", errorData);

    return new ResponseFailedEventData(responseData);
  }

  private ResponseReasoningSummaryPartAddedEventData createReasoningAddedEvent(ThinkingStartEvent event) {
    int summaryIndex = getNextSummaryIndex();

    return new ResponseReasoningSummaryPartAddedEventData(summaryIndex);
  }

  private ResponseOutputItemDoneEventData createReasoningDoneEvent(ThinkingEndEvent event) {
    // For now, return an empty done event for reasoning
    // In a real implementation, we'd have the final reasoning text
    Map<String, Object> item = new HashMap<>();
    item.put("type", "message");
    item.put("role", "assistant");
    item.put("content", new ArrayList<>());

    return new ResponseOutputItemDoneEventData(item, 0);
  }

  private ResponseOutputItemAddedEventData createToolCallAddedEvent(ToolCallStartEvent event) {
    String callId = event.getToolCallId();
    String toolName = event.getToolCallName();
    int outputIndex = getNextOutputIndex();

    // Initialize the arguments accumulator
    toolCallArguments.put(callId, new StringBuilder());
    toolCallNames.put(callId, toolName);

    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("id", callId);
    item.put("name", toolName);
    item.put("arguments", "{}"); // Initially empty

    return new ResponseOutputItemAddedEventData(item, outputIndex);
  }

  private ResponseOutputTextDeltaEventData createToolCallArgsEvent(ToolCallArgsEvent event) {
    String callId = event.getToolCallId();

    // Accumulate the arguments for this tool call
    StringBuilder sb = toolCallArguments.computeIfAbsent(callId, k -> new StringBuilder());
    sb.append(event.getDelta());

    int outputIndex = toolCallNames.containsKey(callId) ? getNextOutputIndex() : 0;

    // Return a text delta event for the arguments
    return new ResponseOutputTextDeltaEventData(event.getDelta(), outputIndex);
  }

  private ResponseOutputItemDoneEventData createToolCallDoneEvent(ToolCallEndEvent event) {
    String callId = event.getToolCallId();

    // Get the accumulated arguments
    StringBuilder sb = toolCallArguments.get(callId);
    String arguments = sb != null ? sb.toString() : "{}";

    String toolName = toolCallNames.get(callId);
    if (toolName == null) {
      toolName = "unknown";
    }

    // Create the item for the done event
    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("id", callId);
    item.put("name", toolName);
    item.put("arguments", arguments);

    // Find the output index for this call
    int outputIndex = toolCallNames.containsKey(callId) ? getNextOutputIndex() : 0;

    // Clean up the tracking maps
    toolCallArguments.remove(callId);
    toolCallNames.remove(callId);

    return new ResponseOutputItemDoneEventData(item, outputIndex);
  }

  private ResponseOutputItemAddedEventData createToolCallResultEvent(ToolCallResultEvent event) {
    String callId = event.getToolCallId();
    int outputIndex = getNextOutputIndex();

    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call_output");
    item.put("call_id", callId);
    item.put("output", event.getContent());

    return new ResponseOutputItemAddedEventData(item, outputIndex);
  }

  private ResponseOutputItemAddedEventData createTextMessageAddedEvent(TextMessageStartEvent event) {
    int outputIndex = getNextOutputIndex();

    Map<String, Object> item = new HashMap<>();
    item.put("type", "message");
    item.put("role", "assistant"); // Assuming assistant role for responses
    item.put("content", new ArrayList<>()); // Initially empty content array

    return new ResponseOutputItemAddedEventData(item, outputIndex);
  }

  private ResponseOutputTextDeltaEventData createTextDeltaEvent(TextMessageChunkEvent event) {
    String itemId = event.getMessageId();

    Integer outputIndex = outputIndices.get(itemId);
    if (outputIndex == null) {
      outputIndex = getNextOutputIndex();
      outputIndices.put(itemId, outputIndex);
    }

    return new ResponseOutputTextDeltaEventData(event.getDelta(), outputIndex);
  }

  private ResponseOutputItemDoneEventData createTextMessageDoneEvent(TextMessageEndEvent event) {
    String itemId = event.getMessageId();

    // Find the output index for this item
    Integer outputIndex = outputIndices.get(itemId);
    if (outputIndex == null) {
      outputIndex = getNextOutputIndex();
    }

    Map<String, Object> item = new HashMap<>();
    item.put("type", "message");
    item.put("role", "assistant");
    item.put("content", new ArrayList<>()); // Content would be built from deltas

    return new ResponseOutputItemDoneEventData(item, outputIndex);
  }

  private ResponseOutputItemAddedEventData createTextContentEvent(TextMessageContentEvent event) {
    String itemId = event.getMessageId();
    int outputIndex = getNextOutputIndex();

    // Create a message item with content
    Map<String, Object> contentItem = new HashMap<>();
    contentItem.put("type", "output_text");
    contentItem.put("text", event.getDelta());

    List<Map<String, Object>> contentList = new ArrayList<>();
    contentList.add(contentItem);

    Map<String, Object> item = new HashMap<>();
    item.put("type", "message");
    item.put("role", "assistant");
    item.put("content", contentList);

    return new ResponseOutputItemAddedEventData(item, outputIndex);
  }

  private ResponseInProgressEventData createResponseInProgressEvent(StepStartedEvent event) {
    Map<String, Object> data = new HashMap<>();
    data.put("status", "in_progress");
    return new ResponseInProgressEventData(data);
  }

  private int getNextOutputIndex() {
    return globalOutputIndex++;
  }

  private int getNextContentIndex() {
    return globalContentIndex++;
  }

  private int getNextSummaryIndex() {
    return globalSummaryIndex++;
  }

  private boolean isUpdatePlanCall(String callId) {
    return updatePlanCalls.getOrDefault(callId, false);
  }

  private ResponseOutputItemAddedEventData createUpdatePlanAddedEvent(ToolCallStartEvent event) {
    String callId = event.getToolCallId();
    int outputIndex = getNextOutputIndex();

    updatePlanCalls.put(callId, true);
    toolCallArguments.put(callId, new StringBuilder());
    toolCallNames.put(callId, "update_plan");

    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("id", callId);
    item.put("name", "update_plan");
    item.put("arguments", "{}"); // Initially empty

    return new ResponseOutputItemAddedEventData(item, outputIndex);
  }

  private ResponseOutputTextDeltaEventData createUpdatePlanArgsEvent(ToolCallArgsEvent event) {
    String callId = event.getToolCallId();

    StringBuilder sb = toolCallArguments.computeIfAbsent(callId, _ -> new StringBuilder());
    sb.append(event.getDelta());

    int outputIndex = toolCallNames.containsKey(callId) ? getNextOutputIndex() : 0;

    // Return a text delta event for the arguments
    return new ResponseOutputTextDeltaEventData(event.getDelta(), outputIndex);
  }

  private ResponseOutputItemDoneEventData createUpdatePlanDoneEvent(ToolCallEndEvent event) {
    String callId = event.getToolCallId();

    StringBuilder sb = toolCallArguments.get(callId);
    String arguments = sb != null ? sb.toString() : "{}";

    // For update_plan, we need to ensure it's properly formatted for Codex CLI
    Map<String, Object> item = new HashMap<>();
    item.put("type", "function_call");
    item.put("id", callId);
    item.put("name", "update_plan");
    item.put("arguments", arguments);

    // Find the output index for this call
    int outputIndex = toolCallNames.containsKey(callId) ? getNextOutputIndex() : 0;

    // Clean up the tracking maps
    updatePlanCalls.remove(callId);
    toolCallArguments.remove(callId);
    toolCallNames.remove(callId);

    return new ResponseOutputItemDoneEventData(item, outputIndex);
  }
}