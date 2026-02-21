package com.agentengine.engine.api.tools;

import java.util.List;

public interface ToolSuite extends ToolAssetProvider {

  List<ToolProvider> toolProviders();
}
