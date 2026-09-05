package com.agentengine.util.common;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface JsonCodecFactory {

  ObjectMapper getCodec();

  int priority();
}
