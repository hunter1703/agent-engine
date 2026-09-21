package com.agentengine.util.pekko;

import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.actor.ContextualCommand;
import java.util.Optional;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;

public abstract class ContextualShardedEntity<
        Command extends ContextualCommand, Event, State>
    extends AbstractShardedEntity<Command, Event, State> {

  protected ContextualShardedEntity(
      final String typeKeyName, final String entityId, final EventSourcePlugin plugin) {
    super(typeKeyName, entityId, plugin);
  }

  protected abstract CommandHandler<Command, Event, State> contextualCommandHandler();

  protected abstract Context defaultContext(State state);

  @Override
  public final CommandHandler<Command, Event, State> commandHandler() {
    final CommandHandler<Command, Event, State> handler = contextualCommandHandler();
    return (state, command) -> {
      final Context context =
          command.getContext() == null ? defaultContext(state) : command.getContext();
      return context.get(() -> handler.apply(state, command));
    };
  }

  protected final <S extends State> Procedure<S> inContext(final Procedure<S> callback) {
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
