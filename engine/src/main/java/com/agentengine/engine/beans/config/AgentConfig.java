package com.agentengine.engine.beans.config;

public final class AgentConfig implements Config {
  private ToolsConfig tools = new ToolsConfig();
  private EngineConfig engine = new HybridEngineConfig();
  private ContextConfig context = new SummarizingContextConfig();

  private StateStoreConfig stateStore = new MemoryStateStoreConfig();

  @Override
  public void validate() {
    tools.validate();
    engine.validate();
    context.validate();
    stateStore.validate();
  }

  public ToolsConfig getTools() {
    return tools;
  }

  public void setTools(final ToolsConfig tools) {
    this.tools = tools;
  }

  public EngineConfig getEngine() {
    return engine;
  }

  public void setEngine(final EngineConfig engine) {
    this.engine = engine;
  }

  public ContextConfig getContext() {
    return context;
  }

  public void setContext(final ContextConfig context) {
    this.context = context;
  }

  public StateStoreConfig getStateStore() {
    return stateStore;
  }

  public void setStateStore(final StateStoreConfig stateStore) {
    this.stateStore = stateStore;
  }

  public static AgentConfig empty() {
    return new AgentConfig();
  }
}
