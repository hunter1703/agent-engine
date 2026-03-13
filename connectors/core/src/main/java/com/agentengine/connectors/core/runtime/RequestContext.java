package com.agentengine.connectors.core.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record RequestContext(Map<String, Object> input, Object rawPayload, Map<String, Object> auth, Connection connection,
    Map<String, Object> previous, Map<String, Object> vars) {

  public RequestContext {
    input = input == null ? Map.of() : Map.copyOf(input);
    auth = auth == null ? Map.of() : Map.copyOf(auth);
    connection = connection == null ? new Connection(null, Map.of()) : connection;
    previous = previous == null ? Map.of() : Map.copyOf(previous);
    vars = vars == null ? Map.of() : Map.copyOf(vars);
  }

  public static RequestContext empty() {
    return new RequestContext(Map.of(), null, Map.of(), new Connection(null, Map.of()), Map.of(), Map.of());
  }

  public Map<String, Object> toTemplateVariables() {
    final Map<String, Object> map = new HashMap<>();
    map.put("input", input);
    map.put("rawPayload", rawPayload);
    map.put("auth", auth);
    map.put("connection", connection);
    map.put("previous", previous);
    map.put("vars", vars);
    return Collections.unmodifiableMap(map);
  }

  public RequestContext withPrevious(final Map<String, Object> previousValues) {
    return new RequestContext(input, rawPayload, auth, connection, previousValues, vars);
  }

  public RequestContext withVars(final Map<String, Object> variableValues) {
    return new RequestContext(input, rawPayload, auth, connection, previous, variableValues);
  }
}
