package com.agentengine.util.common;

import com.fasterxml.jackson.databind.Module;

public interface CodecModuleProvider {

  Module getModule(boolean includeTypeInfo);
}
