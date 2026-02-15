package com.agentengine.interfaces.rest.responses.dtos;

import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Base class for all response event data types
 */
@Schema(oneOf = {CreatedEventData.class, InProgressEventData.class, OutputTextDeltaEventData.class,
    OutputItemAddedEventData.class, OutputItemDoneEventData.class, ReasoningSummaryPartAddedEventData.class,
    ReasoningSummaryTextDeltaEventData.class, DoneEventData.class, CompletedEventData.class,
    FailedEventData.class}, discriminatorProperty = "type", discriminatorMapping = {
        @DiscriminatorMapping(value = "response.created", schema = CreatedEventData.class),
        @DiscriminatorMapping(value = "response.in_progress", schema = InProgressEventData.class),
        @DiscriminatorMapping(value = "response.output_text.delta", schema = OutputTextDeltaEventData.class),
        @DiscriminatorMapping(value = "response.output_item.added", schema = OutputItemAddedEventData.class),
        @DiscriminatorMapping(value = "response.output_item.done", schema = OutputItemDoneEventData.class),
        @DiscriminatorMapping(value = "response.reasoning_summary_part.added", schema = ReasoningSummaryPartAddedEventData.class),
        @DiscriminatorMapping(value = "response.reasoning_summary_text.delta", schema = ReasoningSummaryTextDeltaEventData.class),
        @DiscriminatorMapping(value = "response.done", schema = DoneEventData.class),
        @DiscriminatorMapping(value = "response.completed", schema = CompletedEventData.class),
        @DiscriminatorMapping(value = "response.failed", schema = FailedEventData.class)})
public abstract class BaseResponsesEventData {
  protected final String type;

  public BaseResponsesEventData(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
