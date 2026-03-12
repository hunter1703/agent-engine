package com.agentengine.interfaces.rest.requests;

import com.agentengine.interfaces.rest.dto.AgentRequest;
import com.agentengine.engine.api.beans.ConfirmationDecision;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ResumeSessionRequest extends AgentRequest {
  private String decision;

  public ResumeSessionRequest() {}

  public ResumeSessionRequest(final String answer) {
    this(answer, null);
  }

  public ResumeSessionRequest(final String answer, final ConfirmationDecision decision) {
    setMessage(answer);
    this.decision = decision == null ? null : decision.name();
  }

  public String getDecision() {
    return decision;
  }

  @JsonIgnore
  public ConfirmationDecision getDecisionEnum() {
    return ConfirmationDecision.valueOfOrDefault(decision);
  }

  public void setDecision(final String decision) {
    this.decision = decision;
  }
}
