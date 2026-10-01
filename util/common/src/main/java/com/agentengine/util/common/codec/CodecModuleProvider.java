package com.agentengine.util.common.codec;

import com.fasterxml.jackson.databind.Module;

public interface CodecModuleProvider {

  Module getModule();
}
