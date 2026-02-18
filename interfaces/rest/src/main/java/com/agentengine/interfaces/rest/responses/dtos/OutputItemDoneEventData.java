package com.agentengine.interfaces.rest.responses.dtos;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(oneOf = {MessageCompletedEventData.class, ToolCallEventData.class, ToolCallResultDoneEventData.class,
    ReasoningDoneEventData.class})
public class OutputItemDoneEventData extends BaseResponsesEventData {
  private final Map<String, Object> item;
  @JsonProperty("output_index")
  private final Integer outputIndex;

  public OutputItemDoneEventData(Map<String, Object> item, int outputIndex) {
    super("response.output_item.done");
    this.item = item;
    this.outputIndex = outputIndex;
  }

  public Map<String, Object> getItem() {
    return item;
  }

  public Integer getOutputIndex() {
    return outputIndex;
  }
}
