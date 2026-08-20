package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.Map;

public class HeaderAuthDecorator implements AuthDecorator<HttpRequest> {
  private final Template<Map<String, String>> headerTemplate;

  public HeaderAuthDecorator(Template<Map<String, String>> headerTemplate) {
    this.headerTemplate = headerTemplate;
  }

  @Override
  public void decorate(HttpRequest request) {
    final Map<String, String> authHeaders =
        CollectionUtils.nullSafeMap(headerTemplate.getValue(request.getParameters()));
    request.addHeaders(authHeaders);
  }
}
