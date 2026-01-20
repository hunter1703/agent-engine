package com.agentengine.engine.client.beans.session;

public record Summary(String id, String content, String summarizedFromMessageId, String summarizedTillMessageId,
    long createdTime) {
}
