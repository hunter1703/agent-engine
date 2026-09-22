package com.agentengine.util.pekko;

import com.agentengine.util.context.Context;
import com.agentengine.util.context.Contextual;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.pekko.actor.ShardedEntity;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EffectBuilder;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;
import org.apache.pekko.persistence.typed.javadsl.SignalHandlerBuilder;

public abstract class ContextualShardedEntity<
        Command extends Contextual, Event, State extends Contextual>
    extends ShardedEntity<Command, Event, State> {

  protected ContextualShardedEntity(
      final String typeKeyName,
      final String entityId,
      final EventSourcePlugin plugin,
      final ActorContext<Command> actorContext) {
    super(typeKeyName, entityId, plugin, actorContext);
  }

  protected abstract CommandHandler<Command, Event, State> contextualCommandHandler();

  protected Context defaultContext(final State state) {
    final Context context = state == null ? null : state.context();
    if (context == null) {
      return new Context(UUID.randomUUID().toString(), UserContext.SYSTEM);
    }
    return context;
  }

  protected void onPostStop(final State state) {}

  protected void onRecoveryCompleted(final State state) {}

  @Override
  public final SignalHandler<State> signalHandler() {
    return SignalHandlerBuilder.<State>builder()
        .onSignal(PostStop.class, (state, _) -> defaultContext(state).run(() -> onPostStop(state)))
        .onSignal(
            RecoveryCompleted.class,
            (state, _) -> defaultContext(state).run(() -> onRecoveryCompleted(state)))
        .build();
  }

  @Override
  public final CommandHandler<Command, Event, State> commandHandler() {
    final CommandHandler<Command, Event, State> handler = contextualCommandHandler();
    return (state, command) -> {
      final Context context = command.context() == null ? defaultContext(state) : command.context();
      return context.get(() -> handler.apply(state, command));
    };
  }

  protected final <T, R> Function2<T, Throwable, R> inContext(
      final Function2<T, Throwable, R> mapResult) {
    final Context context =
        Context.current().orElseThrow(() -> new IllegalStateException("No context to carry"));
    return (value, error) -> context.call(() -> mapResult.apply(value, error));
  }

  protected final <T> void pipeToSelf(
      final CompletionStage<T> future, final Function2<T, Throwable, Command> mapResult) {
    actorContext.pipeToSelf(future, inContext(mapResult));
  }

  protected final EffectBuilder<Event, State> thenRun(
      final EffectBuilder<Event, State> builder, final Procedure<State> callback) {
    return builder.thenRun(inContext(callback));
  }

  private <S extends State> Procedure<S> inContext(final Procedure<S> callback) {
    final Optional<Context> current = Context.current();
    return state ->
        current
            .orElseGet(() -> defaultContext(state))
            .call(
                () -> {
                  callback.apply(state);
                  return null;
                });
  }
}
