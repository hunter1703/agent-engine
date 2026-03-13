package com.agentengine.util.common.builder;

import java.util.Map;

public record LayoutDynamicSchema(String url, String method, Map<String, Object> body) {
}
