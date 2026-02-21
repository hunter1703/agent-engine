package com.agentengine.engine.api.tools;

import java.util.List;

public interface ToolSuite {

    String agentId();

    String suiteName();

    List<ToolProvider> toolProviders();
}
