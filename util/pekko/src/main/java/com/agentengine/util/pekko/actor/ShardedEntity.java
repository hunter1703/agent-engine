package com.agentengine.util.pekko.actor;

import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

/**
 * Abstract base for cluster-sharded, event-sourced entities. Subclasses provide only domain logic
 * via {@link #commandHandler()} and {@link #eventHandler()}. All Pekko boilerplate (persistence ID,
 * snapshot retention, sharding registration) lives here
 *
 * <p>Concrete subclass pattern:
 *
 * <pre>{@code
 * public class FooEntity extends ShardedEntity<FooEntity.Command, FooEntity.Event, FooEntity.State> {
 *   static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "Foo");
 *
 *   FooEntity(String entityId) { super(TYPE_KEY.name(), entityId); }
 *
 *   public State emptyState() { return State.empty(); }
 *   public CommandHandler<Command, Event, State> commandHandler() { ... }
 *   public EventHandler<State, Event> eventHandler() { ... }
 *
 *   // Register sharding and expose entity refs:
 *   public static void init(ActorSystem<?> system) {
 *     ClusterSharding.get(system).init(Entity.of(TYPE_KEY, ctx -> new FooEntity(ctx.getEntityId())));
 *   }
 *   public static EntityRef<Command> entityRef(ActorSystem<?> system, String id) {
 *     return ClusterSharding.get(system).entityRefFor(TYPE_KEY, id);
 *   }
 * }
 * }</pre>
 *
 * @param <Command> command type
 * @param <Event> event type
 * @param <State> state type
 */
public abstract class ShardedEntity<Command, Event, State> extends EventSourcedBehavior<Command, Event, State> {

    /**
     * Derives the persistence ID from the type key name and the shard entity ID, following the Pekko
     * convention of {@code PersistenceId.of(typeKey.name(), entityId)}.
     *
     * <p>Note: {@code getClass().getSimpleName()} cannot be used here because {@code getClass()} is
     * not accessible before the supertype constructor completes. Pass {@code TYPE_KEY.name()} as
     * {@code typeKeyName}.
     *
     * @param typeKeyName the entity type key name — pass {@code TYPE_KEY.name()}
     * @param entityId the shard entity ID
     */
    protected ShardedEntity(final String typeKeyName, final String entityId) {
        super(PersistenceId.of(typeKeyName, entityId));
    }

    @Override
    public abstract State emptyState();

    @Override
    public abstract CommandHandler<Command, Event, State> commandHandler();

    @Override
    public abstract EventHandler<State, Event> eventHandler();
}
