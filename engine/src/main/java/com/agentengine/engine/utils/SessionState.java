package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;

public final class SessionState {
  private boolean paused;
  private String pauseReason;
  private String pausePrompt;
  private List<String> pauseOptions = new ArrayList<>();
  private String pauseInvocationId;
  private Long pauseRequestedAt;

  public SessionState() {}

  public boolean paused() {
    return paused;
  }

  public String pauseReason() {
    return pauseReason;
  }

  public String pausePrompt() {
    return pausePrompt;
  }

  public List<String> pauseOptions() {
    return List.copyOf(pauseOptions);
  }

  public String pauseInvocationId() {
    return pauseInvocationId;
  }

  public Long pauseRequestedAt() {
    return pauseRequestedAt;
  }

  public boolean isPaused() {
    return paused;
  }

  public void setPaused(final boolean paused) {
    this.paused = paused;
  }

  public String getPauseReason() {
    return pauseReason;
  }

  public void setPauseReason(final String pauseReason) {
    this.pauseReason = pauseReason;
  }

  public String getPausePrompt() {
    return pausePrompt;
  }

  public void setPausePrompt(final String pausePrompt) {
    this.pausePrompt = pausePrompt;
  }

  public List<String> getPauseOptions() {
    return List.copyOf(pauseOptions);
  }

  public void setPauseOptions(final List<String> pauseOptions) {
    this.pauseOptions = sanitizeOptions(pauseOptions);
  }

  public String getPauseInvocationId() {
    return pauseInvocationId;
  }

  public void setPauseInvocationId(final String pauseInvocationId) {
    this.pauseInvocationId = pauseInvocationId;
  }

  public Long getPauseRequestedAt() {
    return pauseRequestedAt;
  }

  public void setPauseRequestedAt(final Long pauseRequestedAt) {
    this.pauseRequestedAt = pauseRequestedAt;
  }

  public void markPaused(
      final String prompt,
      final List<String> options,
      final String reason,
      final String invocationId,
      final long requestedAt) {
    paused = true;
    pausePrompt = StringUtils.isBlank(prompt) ? null : prompt;
    pauseOptions = sanitizeOptions(options);
    pauseReason = StringUtils.isBlank(reason) ? null : reason;
    pauseInvocationId = StringUtils.isBlank(invocationId) ? null : invocationId;
    pauseRequestedAt = requestedAt;
  }

  public void clearPause() {
    paused = false;
    pauseReason = null;
    pausePrompt = null;
    pauseOptions = new ArrayList<>();
    pauseInvocationId = null;
    pauseRequestedAt = null;
  }

  private static List<String> sanitizeOptions(final List<String> options) {
    if (options == null || options.isEmpty()) {
      return new ArrayList<>();
    }
    return options.stream()
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }
}

