package com.agentengine.connectors.core.spi;

import java.util.List;

public interface TemplateFunctionProvider {

    String name();

    Object apply(List<Object> args);
}
