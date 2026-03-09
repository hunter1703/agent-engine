package com.agentengine.engine.plugins;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;

/**
 * Composes an ordered list of plugins with middleware semantics:
 * <ul>
 *   <li><b>before*</b> callbacks run in forward order (A → B → …); first non-empty short-circuits.</li>
 *   <li><b>after*</b> callbacks run in reverse order (… → B → A); each stage receives the
 *       output of the previous, forming a sequential pipeline.</li>
 * </ul>
 *
 * <p>Register plugins in logical "outer-to-inner" order. For example,
 * {@code new PluginGroup("engine", List.of(guardrailPlugin, lifecyclePlugin, modelPipeline, contextPlugin))} means:
 * <ul>
 *   <li>Input validation: guardrail first, then request/context processing.</li>
 *   <li>Output processing: model pipeline transforms the raw response first, then guardrail
 *       evaluates the already-processed result.</li>
 * </ul>
 */
public final class PluginGroup extends BasePlugin {

  private final List<BasePlugin> plugins;

  public PluginGroup(final String name, final List<BasePlugin> plugins) {
    super(name);
    this.plugins = List.copyOf(plugins);
  }

  // ── before* : forward order, first non-empty short-circuits ─────────────

  @Override
  public Maybe<Content> onUserMessageCallback(
      final InvocationContext ctx, final Content userMessage) {
    for (final BasePlugin plugin : plugins) {
      final Content result = plugin.onUserMessageCallback(ctx, userMessage).blockingGet();
      if (result != null) {
        return Maybe.just(result);
      }
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<Content> beforeRunCallback(final InvocationContext ctx) {
    for (final BasePlugin plugin : plugins) {
      final Content result = plugin.beforeRunCallback(ctx).blockingGet();
      if (result != null) {
        return Maybe.just(result);
      }
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<Content> beforeAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    for (final BasePlugin plugin : plugins) {
      final Content result = plugin.beforeAgentCallback(agent, ctx).blockingGet();
      if (result != null) {
        return Maybe.just(result);
      }
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext ctx, final LlmRequest.Builder requestBuilder) {
    for (final BasePlugin plugin : plugins) {
      final LlmResponse result = plugin.beforeModelCallback(ctx, requestBuilder).blockingGet();
      if (result != null) {
        return Maybe.just(result);
      }
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<Map<String, Object>> beforeToolCallback(
      final BaseTool tool, final Map<String, Object> args, final ToolContext toolContext) {
    for (final BasePlugin plugin : plugins) {
      final Map<String, Object> result = plugin.beforeToolCallback(tool, args, toolContext).blockingGet();
      if (result != null) {
        return Maybe.just(result);
      }
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<Event> onEventCallback(final InvocationContext ctx, final Event event) {
    // forward order; first non-empty wins
    for (final BasePlugin p : plugins) {
      final Event result = p.onEventCallback(ctx, event).blockingGet();
      if (result != null) return Maybe.just(result);
    }
    return Maybe.empty();
  }

  // ── after* : reverse order, sequential pipeline ──────────────────────────

  @Override
  public Maybe<Map<String, Object>> afterToolCallback(
          final BaseTool tool,
          final Map<String, Object> args,
          final ToolContext toolContext,
          final Map<String, Object> toolResult) {
    boolean changed = false;
    Map<String, Object> current = toolResult;
    for (final BasePlugin plugin : plugins.reversed()) {
      final Map<String, Object> result =
              plugin.afterToolCallback(tool, args, toolContext, current).blockingGet();
      if (result != null) {
        current = result;
        changed = true;
      }
    }
    return changed ? Maybe.just(current) : Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
          final CallbackContext ctx, final LlmResponse llmResponse) {
    boolean changed = false;
    LlmResponse current = llmResponse;
    for (final BasePlugin plugin : plugins.reversed()) {
      final LlmResponse result = plugin.afterModelCallback(ctx, current).blockingGet();
      if (result != null) {
        current = result;
        changed = true;
      }
    }
    return changed ? Maybe.just(current) : Maybe.empty();
  }

  @Override
  public Maybe<Content> afterAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    for (final BasePlugin plugin : plugins.reversed()) {
      final Content result = plugin.afterAgentCallback(agent, ctx).blockingGet();
      if (result != null) {
        return Maybe.just(result);
      }
    }
    return Maybe.empty();
  }

  @Override
  public Completable afterRunCallback(final InvocationContext ctx) {
    Completable chain = Completable.complete();
    for (final BasePlugin plugin : plugins.reversed()) {
      chain = chain.andThen(plugin.afterRunCallback(ctx));
    }
    return chain;
  }
}
