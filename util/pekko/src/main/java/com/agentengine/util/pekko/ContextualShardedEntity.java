package com.agentengine.util.pekko;

import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.actor.ContextualCommand;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;

public abstract class ContextualShardedEntity<
        Command extends ContextualCommand, Event, State>
    extends AbstractShardedEntity<Command, Event, State> {

  protected final Context defaultContext;

  protected ContextualShardedEntity(
      final String typeKeyName,
      final String entityId,
      final EventSourcePlugin plugin,
      final Context defaultContext) {
    super(typeKeyName, entityId, plugin);
    this.defaultContext = defaultContext;
  }

  protected abstract CommandHandler<Command, Event, State> contextualCommandHandler();

  @Override
  public final CommandHandler<Command, Event, State> commandHandler() {
    final CommandHandler<Command, Event, State> handler = contextualCommandHandler();
    return (state, command) -> {
      final Context context =
          command.getContext() == null ? defaultContext : command.getContext();
      return context.get(() -> handler.apply(state, command));
    };
  }

  protected final <S> Procedure<S> inContext(final Procedure<S> callback) {
    final Context context = Context.current().orElse(defaultContext);
    return state ->
        context.call(
            () -> {
              callback.apply(state);
              return null;
            });
  }
}
