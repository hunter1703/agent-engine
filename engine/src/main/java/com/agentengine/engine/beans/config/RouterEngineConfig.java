package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(typeName = "router")
public final class RouterEngineConfig extends AbstractEngineConfig {
  private String router;
  private String tool;
  private String heavy;

  public RouterEngineConfig() {
    super(EngineType.ROUTER);
  }

  public String getRouter() {
    return router;
  }

  public void setRouter(final String router) {
    this.router = router;
  }

  public String getTool() {
    return tool;
  }

  public void setTool(final String tool) {
    this.tool = tool;
  }

  public String getHeavy() {
    return heavy;
  }

  public void setHeavy(final String heavy) {
    this.heavy = heavy;
  }

  @Override
  public void validate() {
    validateBase();
    if (router == null || router.isBlank()) {
      throw new IllegalArgumentException("engine.router is required");
    }
    if ((tool == null || tool.isBlank()) && (heavy == null || heavy.isBlank())) {
      throw new IllegalArgumentException("engine.router requires engine.tool or engine.heavy");
    }
  }
}
