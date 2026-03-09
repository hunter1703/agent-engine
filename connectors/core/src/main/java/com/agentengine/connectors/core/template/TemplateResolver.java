package com.agentengine.connectors.core.template;

import com.agentengine.connectors.core.runtime.RequestContext;

public interface TemplateResolver {

  ResolvedValue resolve(Object template, RequestContext context, TemplateResolutionOptions options);
}
