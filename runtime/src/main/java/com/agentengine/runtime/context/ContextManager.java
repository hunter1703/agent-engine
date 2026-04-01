package com.agentengine.runtime.context;

import com.google.genai.types.Content;
import java.util.List;

public interface ContextManager {

    List<Content> buildPrompt(final String agentId, final String sessionId, final List<Content> contents);
}
