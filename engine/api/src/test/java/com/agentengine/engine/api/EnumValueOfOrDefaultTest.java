package com.agentengine.engine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.beans.config.AgentConfig.AgentType;
import com.agentengine.engine.api.beans.config.ContextManagerConfig.ContextType;
import com.agentengine.engine.api.beans.config.ModelConfig.Provider;
import com.agentengine.engine.api.beans.config.SessionServiceConfig.SessionServiceType;
import com.agentengine.engine.api.query.Operator;
import com.agentengine.engine.api.update.OperationType;
import org.junit.jupiter.api.Test;

class EnumValueOfOrDefaultTest {

  @Test
  void parsesKnownEnumValues() {
    assertEquals(Operator.AND, Operator.valueOfOrDefault("and"));
    assertEquals(OperationType.SET, OperationType.valueOfOrDefault("SET"));
    assertEquals(RequestType.STREAM_AGUI_EVENTS, RequestType.valueOfOrDefault("stream_agui_events"));
    assertEquals(AgentType.DEFAULT, AgentType.valueOfOrDefault("default"));
    assertEquals(SessionServiceType.MEMORY, SessionServiceType.valueOfOrDefault("memory"));
    assertEquals(ContextType.SUMMARIZE, ContextType.valueOfOrDefault("summarize"));
    assertEquals(ContextType.LAST_N, ContextType.valueOfOrDefault("last_n"));
    assertEquals(Provider.OLLAMA, Provider.valueOfOrDefault("ollama"));
  }

  @Test
  void returnsUnknownForInvalidValues() {
    assertEquals(Operator.UNKNOWN, Operator.valueOfOrDefault("nope"));
    assertEquals(OperationType.UNKNOWN, OperationType.valueOfOrDefault("oops"));
    assertEquals(RequestType.UNKNOWN, RequestType.valueOfOrDefault("wrong"));
    assertEquals(AgentType.UNKNOWN, AgentType.valueOfOrDefault("ghost"));
    assertEquals(SessionServiceType.UNKNOWN, SessionServiceType.valueOfOrDefault("file"));
    assertEquals(ContextType.UNKNOWN, ContextType.valueOfOrDefault("none"));
    assertEquals(Provider.UNKNOWN, Provider.valueOfOrDefault("unknown_provider"));
  }
}
