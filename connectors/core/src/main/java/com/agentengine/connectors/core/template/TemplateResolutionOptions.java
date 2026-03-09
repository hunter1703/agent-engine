package com.agentengine.connectors.core.template;

public record TemplateResolutionOptions(boolean strictUnresolvedVariables, boolean omitNulls) {

  public static TemplateResolutionOptions strict() {
    return new TemplateResolutionOptions(true, false);
  }

  public static TemplateResolutionOptions strictWithOmitNulls(final boolean omitNulls) {
    return new TemplateResolutionOptions(true, omitNulls);
  }

  public static TemplateResolutionOptions lenientWithOmitNulls(final boolean omitNulls) {
    return new TemplateResolutionOptions(false, omitNulls);
  }
}
