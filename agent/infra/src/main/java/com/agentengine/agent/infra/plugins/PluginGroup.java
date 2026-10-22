package com.agentengine.agent.infra.plugins;

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
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Composes an ordered list of plugins as layers of an onion: a call goes in through the layers from
 * the outermost to the innermost, and what comes out goes back through them in reverse.
 *
 * <ul>
 *   <li><b>A rewrite chains.</b> A callback that receives something and may return a changed
 *       version of it ({@code onUserMessage} on the way in; {@code onEvent}, {@code afterModel} and
 *       {@code afterTool} on the way out) hands each plugin what the previous one returned. Empty
 *       leaves it as it is.
 *   <li><b>An answer short-circuits.</b> A callback that may answer in place of the call it comes
 *       before ({@code beforeRun}, {@code beforeAgent}, {@code beforeModel}, {@code beforeTool}), or
 *       add to the agent's output ({@code afterAgent}), stops at the first plugin that answers.
 * </ul>
 *
 * <p>Register plugins in logical "outer-to-inner" order. For example, {@code new
 * PluginGroup("engine", List.of(guardrailPlugin, lifecyclePlugin, modelPipeline, contextPlugin))}
 * means:
 *
 * <ul>
 *   <li>Input validation: guardrail first, then request/context processing.
 *   <li>Output processing: model pipeline transforms the raw response first, then guardrail
 *       evaluates the already-processed result.
 * </ul>
 */
public final class PluginGroup extends BasePlugin {

  private final List<BasePlugin> plugins;

  public PluginGroup(final String name, final List<BasePlugin> plugins) {
    super(name);
    this.plugins = List.copyOf(plugins);
  }

  // ── way in, forward order ────────────────────────────────────────────────

  /** A rewrite: each plugin sees the message as the one before it left it. */
  @Override
  public Maybe<Content> onUserMessageCallback(
      final InvocationContext ctx, final Content userMessage) {
    return pipeline(
        plugins, userMessage, (plugin, current) -> plugin.onUserMessageCallback(ctx, current));
  }

  // The rest answer in place of the call they come before, so the first plugin that answers
  // short-circuits.

  @Override
  public Maybe<Content> beforeRunCallback(final InvocationContext ctx) {
    return firstResult(plugin -> plugin.beforeRunCallback(ctx));
  }

  @Override
  public Maybe<Content> beforeAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    return firstResult(plugin -> plugin.beforeAgentCallback(agent, ctx));
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext ctx, final LlmRequest.Builder requestBuilder) {
    return firstResult(plugin -> plugin.beforeModelCallback(ctx, requestBuilder));
  }

  @Override
  public Maybe<Map<String, Object>> beforeToolCallback(
      final BaseTool tool, final Map<String, Object> args, final ToolContext toolContext) {
    return firstResult(plugin -> plugin.beforeToolCallback(tool, args, toolContext));
  }

  // ── way out, reverse order ───────────────────────────────────────────────

  /** A rewrite: each plugin sees the event as the one inside it left it. */
  @Override
  public Maybe<Event> onEventCallback(final InvocationContext ctx, final Event event) {
    return pipeline(
        plugins.reversed(), event, (plugin, current) -> plugin.onEventCallback(ctx, current));
  }

  @Override
  public Maybe<Map<String, Object>> afterToolCallback(
      final BaseTool tool,
      final Map<String, Object> args,
      final ToolContext toolContext,
      final Map<String, Object> toolResult) {
    return pipeline(
        plugins.reversed(),
        toolResult,
        (plugin, current) -> plugin.afterToolCallback(tool, args, toolContext, current));
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext ctx, final LlmResponse llmResponse) {
    return pipeline(
        plugins.reversed(),
        llmResponse,
        (plugin, current) -> plugin.afterModelCallback(ctx, current));
  }

  @Override
  public Maybe<Content> afterAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    return Flowable.fromIterable(plugins.reversed())
        .concatMapMaybe(plugin -> plugin.afterAgentCallback(agent, ctx))
        .firstElement();
  }

  @Override
  public Completable afterRunCallback(final InvocationContext ctx) {
    Completable chain = Completable.complete();
    for (final BasePlugin plugin : plugins.reversed()) {
      chain = chain.andThen(plugin.afterRunCallback(ctx));
    }
    return chain;
  }

  /** The first plugin's result, asking the plugins in order and stopping at the first that has one. */
  private <T> Maybe<T> firstResult(final Function<BasePlugin, Maybe<T>> callback) {
    return Flowable.fromIterable(plugins).concatMapMaybe(callback::apply).firstElement();
  }

  /**
   * Hands {@code initial} through {@code ordered}, each plugin seeing what the one before returned;
   * empty when no plugin changed it.
   */
  private <T> Maybe<T> pipeline(
      final List<BasePlugin> ordered,
      final T initial,
      final BiFunction<BasePlugin, T, Maybe<T>> callback) {
    Single<Stage<T>> stages = Single.just(new Stage<>(initial, false));
    for (final BasePlugin plugin : ordered) {
      stages =
          stages.flatMap(
              stage ->
                  callback
                      .apply(plugin, stage.value())
                      .map(changed -> new Stage<>(changed, true))
                      .defaultIfEmpty(stage));
    }
    return stages.flatMapMaybe(
        stage -> stage.changed() ? Maybe.just(stage.value()) : Maybe.empty());
  }

  private record Stage<T>(T value, boolean changed) {}
}
