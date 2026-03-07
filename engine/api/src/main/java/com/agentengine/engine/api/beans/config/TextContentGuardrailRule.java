package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("TEXT_CONTENT")
@BsonDiscriminator(value = "TEXT_CONTENT")
public class TextContentGuardrailRule extends GuardrailRule {
  private Integer maxTextLength;
  private List<String> blockedPatterns = new ArrayList<>();

  public TextContentGuardrailRule() {
    super(GuardrailRuleType.TEXT_CONTENT);
    setStage(GuardrailStage.INPUT);
  }

  public Integer getMaxTextLength() {
    return maxTextLength;
  }

  public void setMaxTextLength(final Integer maxTextLength) {
    this.maxTextLength = maxTextLength;
  }

  public List<String> getBlockedPatterns() {
    return blockedPatterns;
  }

  public void setBlockedPatterns(final List<String> blockedPatterns) {
    this.blockedPatterns = blockedPatterns == null ? new ArrayList<>() : new ArrayList<>(blockedPatterns);
  }
}
