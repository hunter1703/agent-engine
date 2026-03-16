package com.agentengine.connectors.core.config;

import java.util.List;
import java.util.Map;

public record ConnectorDefinition(String id, String appName, EndpointConfig endpoint, Map<String, Object> headers,
    Map<String, Object> query, BodyConfig body, AuthConfig auth, RetryPolicyConfig retryPolicy, PaginationConfig pagination,
    ResponseMappingConfig responseMapping, List<ErrorMappingRule> errorMappings, boolean strictUnresolvedVariables) {

  public ConnectorDefinition {
    appName = appName == null || appName.isBlank() ? id : appName;
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    query = query == null ? Map.of() : Map.copyOf(query);
    body = body == null ? new BodyConfig(BodyType.UNKNOWN, null, null, true) : body;
    auth = auth == null
        ? new AuthConfig(AuthType.NONE, null, null, null, null, null, null, null, null, null, null, null, null, Map.of())
        : auth;
    retryPolicy = retryPolicy == null ? RetryPolicyConfig.disabled() : retryPolicy;
    pagination = pagination == null ? PaginationConfig.none() : pagination;
    responseMapping = responseMapping == null ? new ResponseMappingConfig("${response}", null, null, null, List.of(), true) : responseMapping;
    errorMappings = errorMappings == null ? List.of() : List.copyOf(errorMappings);
  }
}
