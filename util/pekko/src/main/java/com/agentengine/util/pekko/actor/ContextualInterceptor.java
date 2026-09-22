package com.agentengine.util.pekko.actor;

import com.agentengine.util.context.Context;
import com.agentengine.util.context.Contextual;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.TypedActorContext;

public class ContextualInterceptor<T extends Contextual> extends BehaviorInterceptor<T, T> {

  private final Context defaultContext;

  public ContextualInterceptor(final Class<T> commandClass, final Context defaultContext) {
    super(commandClass);
    this.defaultContext = defaultContext;
  }

  @Override
  public Behavior<T> aroundReceive(
      final TypedActorContext<T> actorContext, final T message, final ReceiveTarget<T> target) {
    final Context context = message.context() != null ? message.context() : defaultContext;
    return context.get(() -> target.apply(actorContext, message));
  }
}
