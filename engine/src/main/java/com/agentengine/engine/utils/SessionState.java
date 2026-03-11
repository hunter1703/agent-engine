package com.agentengine.engine.utils;

import java.util.List;

public final class SessionState {
  private PauseState pause = new PauseState();
  private ActiveRunState activeRun;

  public SessionState() {}

  public PauseState pause() {
    return ensurePause();
  }

  public ActiveRunState activeRun() {
    return activeRun;
  }

  public PauseState getPause() {
    return ensurePause();
  }

  public void setPause(final PauseState pause) {
    this.pause = pause == null ? new PauseState() : pause;
  }

  public ActiveRunState getActiveRun() {
    return activeRun;
  }

  public void setActiveRun(final ActiveRunState activeRun) {
    this.activeRun = activeRun;
  }

  public boolean paused() {
    return ensurePause().paused();
  }

  public String pauseReason() {
    return ensurePause().reason();
  }

  public String pausePrompt() {
    return ensurePause().prompt();
  }

  public List<String> pauseOptions() {
    return ensurePause().options();
  }

  public String pauseInvocationId() {
    return ensurePause().invocationId();
  }

  public Long pauseRequestedAt() {
    return ensurePause().requestedAt();
  }

  public boolean isPaused() {
    return ensurePause().isPaused();
  }

  public void setPaused(final boolean paused) {
    ensurePause().setPaused(paused);
  }

  public String getPauseReason() {
    return ensurePause().getReason();
  }

  public void setPauseReason(final String pauseReason) {
    ensurePause().setReason(pauseReason);
  }

  public String getPausePrompt() {
    return ensurePause().getPrompt();
  }

  public void setPausePrompt(final String pausePrompt) {
    ensurePause().setPrompt(pausePrompt);
  }

  public List<String> getPauseOptions() {
    return ensurePause().getOptions();
  }

  public void setPauseOptions(final List<String> pauseOptions) {
    ensurePause().setOptions(pauseOptions);
  }

  public String getPauseInvocationId() {
    return ensurePause().getInvocationId();
  }

  public void setPauseInvocationId(final String pauseInvocationId) {
    ensurePause().setInvocationId(pauseInvocationId);
  }

  public Long getPauseRequestedAt() {
    return ensurePause().getRequestedAt();
  }

  public void setPauseRequestedAt(final Long pauseRequestedAt) {
    ensurePause().setRequestedAt(pauseRequestedAt);
  }

  public String pendingToolName() {
    return ensurePause().pendingToolName();
  }

  public String getPendingToolName() {
    return ensurePause().getPendingToolName();
  }

  public void setPendingToolName(final String pendingToolName) {
    ensurePause().setPendingToolName(pendingToolName);
  }

  public String pendingConfirmationId() {
    return ensurePause().pendingConfirmationId();
  }

  public String getPendingConfirmationId() {
    return ensurePause().getPendingConfirmationId();
  }

  public void setPendingConfirmationId(final String pendingConfirmationId) {
    ensurePause().setPendingConfirmationId(pendingConfirmationId);
  }

  public String approvedToolName() {
    return ensurePause().approvedToolName();
  }

  public String getApprovedToolName() {
    return ensurePause().getApprovedToolName();
  }

  public void setApprovedToolName(final String approvedToolName) {
    ensurePause().setApprovedToolName(approvedToolName);
  }

  public void markPaused(
      final String prompt,
      final List<String> options,
      final String reason,
      final String invocationId,
      final long requestedAt) {
    ensurePause().markPaused(prompt, options, reason, invocationId, requestedAt);
  }

  public void markPaused(
      final String prompt,
      final List<String> options,
      final String reason,
      final String invocationId,
      final long requestedAt,
      final String pendingToolName) {
    ensurePause().markPaused(prompt, options, reason, invocationId, requestedAt, pendingToolName);
  }

  public void markPaused(
      final String prompt,
      final List<String> options,
      final String reason,
      final String invocationId,
      final long requestedAt,
      final String pendingToolName,
      final String pendingConfirmationId) {
    ensurePause()
        .markPaused(
            prompt,
            options,
            reason,
            invocationId,
            requestedAt,
            pendingToolName,
            pendingConfirmationId);
  }

  public void clearPause() {
    ensurePause().clearPause();
  }

  public void markToolApproved(final String toolName) {
    ensurePause().markToolApproved(toolName);
  }

  public boolean consumeApprovedTool(final String toolName) {
    return ensurePause().consumeApprovedTool(toolName);
  }

  private PauseState ensurePause() {
    if (pause == null) {
      pause = new PauseState();
    }
    return pause;
  }
}
