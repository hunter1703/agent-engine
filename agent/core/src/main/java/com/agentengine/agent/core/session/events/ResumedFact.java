package com.agentengine.agent.core.session.events;

import com.agentengine.util.agents.beans.ResumeRequest;

public final class ResumedFact extends SessionFact {

  private ResumeRequest resumeRequest;

  public ResumedFact() {}

  public ResumedFact(final ResumeRequest resumeRequest) {
    this.resumeRequest = resumeRequest;
  }

  public ResumeRequest getResumeRequest() {
    return resumeRequest;
  }

  public void setResumeRequest(final ResumeRequest resumeRequest) {
    this.resumeRequest = resumeRequest;
  }
}
