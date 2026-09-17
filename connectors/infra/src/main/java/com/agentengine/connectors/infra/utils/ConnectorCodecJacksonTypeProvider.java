package com.agentengine.connectors.infra.utils;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.util.List;

public interface ConnectorCodecJacksonTypeProvider {

  List<NamedType> getTypes();
}
