package com.agentengine.util.agents.beans;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Objects;

public final class ResumeRequest {
  @NotBlank(message = "Interrupt Id is required")
  private String interruptId;

  private Boolean accepted;

  private Map<String, Object> answer;

  private String author;

  private long timestamp = System.currentTimeMillis();

  public ResumeRequest() {}

  public ResumeRequest(String interruptId, Boolean accepted, Map<String, Object> answer) {
    this(interruptId, accepted, answer, com.agentengine.util.agents.Constants.AUTHOR_USER);
  }

  public ResumeRequest(
      String interruptId, Boolean accepted, Map<String, Object> answer, String author) {
    this.interruptId = interruptId;
    this.accepted = accepted;
    this.answer = answer;
    this.author = author;
  }

  public String getInterruptId() {
    return interruptId;
  }

  public Boolean getAccepted() {
    return accepted;
  }

  public Map<String, Object> getAnswer() {
    return answer;
  }

  public String getAuthor() {
    return author;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setInterruptId(final String interruptId) {
    this.interruptId = interruptId;
  }

  public void setAccepted(final Boolean accepted) {
    this.accepted = accepted;
  }

  public void setAnswer(final Map<String, Object> answer) {
    this.answer = answer;
  }

  public void setAuthor(final String author) {
    this.author = author;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ResumeRequest) obj;
    return Objects.equals(this.interruptId, that.interruptId)
        && Objects.equals(this.accepted, that.accepted)
        && Objects.equals(this.answer, that.answer)
        && Objects.equals(this.author, that.author)
        && this.timestamp == that.timestamp;
  }

  @Override
  public int hashCode() {
    return Objects.hash(interruptId, accepted, answer, author, timestamp);
  }

  @Override
  public String toString() {
    return "ResumeRequest["
        + "interruptId="
        + interruptId
        + ", "
        + "accepted="
        + accepted
        + ", "
        + "answer="
        + answer
        + ", "
        + "author="
        + author
        + ", "
        + "timestamp="
        + timestamp
        + ']';
  }
}
