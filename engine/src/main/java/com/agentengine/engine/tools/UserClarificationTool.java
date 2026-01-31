package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ExitLoopTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Tool;
import io.reactivex.rxjava3.core.Single;
import io.vertx.json.schema.common.dsl.Schemas;
import io.vertx.json.schema.common.dsl.StringSchemaBuilder;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class UserClarificationTool {
  public static final FunctionTool INSTANCE = FunctionTool.create(UserClarificationTool.class, "clarifyFromUser");

  public static Map<String, Object> clarifyFromUser(final String question) {
    return Map.of("clarification", question);
  }
}
