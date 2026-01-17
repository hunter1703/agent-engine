package com.agentengine.engine.beans;

public record Summary(
    String id,
    String content,
    String summarizedFromMessageId,
    String summarizedTillMessageId,
    long createdTime) {}

