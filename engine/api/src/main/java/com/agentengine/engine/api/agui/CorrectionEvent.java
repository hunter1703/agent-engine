package com.agentengine.engine.api.agui;

import com.agui.core.event.CustomEvent;
import java.util.Map;

public class CorrectionEvent extends CustomEvent {
  private String correctionType;
  private String code;
  private String message;

  public CorrectionEvent(final CorrectionMetadata correctionMetadata) {
    setRawEvent(Map.of("type", "correction"));
    this.correctionType = correctionMetadata.type();
    this.code = correctionMetadata.code();
    this.message = correctionMetadata.message();
  }

  public String getCorrectionType() {
    return correctionType;
  }

  public void setCorrectionType(final String correctionType) {
    this.correctionType = correctionType;
  }

  public String getCode() {
    return code;
  }

  public void setCode(final String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }
}
