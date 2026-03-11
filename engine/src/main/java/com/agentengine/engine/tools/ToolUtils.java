package com.agentengine.engine.tools;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.SchemaUtils;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ToolUtils {

  private ToolUtils() {}

  public static String buildToolMessage(final List<BaseTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder("<AVAILABLE_TOOLS>\n");
    for (BaseTool tool : tools) {
      if (tool == null || tool.name() == null || tool.name().isBlank()) {
        continue;
      }
      String line = "- " + tool.name();
      if (tool.description() != null && !tool.description().isBlank()) {
        line += " - " + tool.description();
      }
      final FunctionDeclaration declaration =
          tool.declaration().orElse(FunctionDeclaration.builder().build());
      builder
          .append(line)
          .append("\n\t-")
          .append("tool args schema - ")
          .append(renderSchema(declaration))
          .append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return builder.toString();
  }

  private static String renderSchema(final FunctionDeclaration declaration) {
    if (declaration == null) {
      return SchemaUtils.toJsonSchema(null);
    }
    if (declaration.parameters().isPresent()) {
      return SchemaUtils.toJsonSchema(declaration.parameters().orElse(null));
    }
    if (declaration.parametersJsonSchema().isPresent()) {
      return JsonUtils.toJson(declaration.parametersJsonSchema().orElse(null));
    }
    return SchemaUtils.toJsonSchema(null);
  }

  public static boolean hasToolParts(final LlmResponse response) {
    return response.content().flatMap(Content::parts).stream()
        .flatMap(List::stream)
        .anyMatch(part -> part.functionCall().isPresent() || part.functionResponse().isPresent());
  }

  public static List<FunctionCall> extractToolCalls(final LlmResponse response) {
    return response.content().flatMap(Content::parts).stream()
        .flatMap(List::stream)
        .map(Part::functionCall)
        .flatMap(Optional::stream)
        .toList();
  }

  public static String summarizeToolParts(final List<Part> parts) {
    if (CollectionUtils.isEmpty(parts)) {
      return "";
    }
    final List<String> calls = new ArrayList<>();
    final List<String> responses = new ArrayList<>();
    for (final Part part : parts) {
      part.functionCall().ifPresent(call -> calls.add(call.name().orElse("unknown")));
      part.functionResponse()
          .ifPresent(response -> responses.add(response.name().orElse("unknown")));
    }
    final List<String> segments = new ArrayList<>();
    final String callSummary = summarizeNames(calls);
    if (!callSummary.isBlank()) {
      segments.add("toolCalls=[" + callSummary + "]");
    }
    final String responseSummary = summarizeNames(responses);
    if (!responseSummary.isBlank()) {
      segments.add("toolResponses=[" + responseSummary + "]");
    }
    return String.join(", ", segments);
  }

  private static String summarizeNames(final List<String> names) {
    if (CollectionUtils.isEmpty(names)) {
      return "";
    }
    final List<String> unique =
        names.stream().filter(name -> name != null && !name.isBlank()).distinct().toList();
    if (unique.isEmpty()) {
      return "";
    }
    final String joined = unique.stream().limit(3).collect(Collectors.joining(", "));
    return unique.size() > 3 ? joined + ", ..." : joined;
  }
}
