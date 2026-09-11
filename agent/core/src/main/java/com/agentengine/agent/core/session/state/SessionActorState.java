package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.PekkoSerializable;
import com.google.adk.events.Event;
import java.util.*;

/**
 * Durable actor state reconstructed from journal facts.
 *
 * <p>{@code grants} accumulates additively across every run of the session: {@link
 * #withNewRun(UniqueRecord, long)} merges each new message's {@link ResourceGrants} on top of
 * whatever was already granted (see {@link ResourceGrants#merge}), so knowledge/notebook access
 * granted in an earlier run is never lost in a later one.
 *
 * <p>{@code runs} keeps every run the session has ever started, for its whole lifetime — {@link
 * #findRunStartSequence(String)} needs the full history to locate any historical run's start, since
 * a rollback can target any run, not only the most recent one. The session's currently active run,
 * if any, is always {@link #currentRun}, its last element.
 */
public record SessionActorState(
    SessionState sessionState,
    Queue<UniqueRecord<UserMessage>> queue,
    Map<String, ChildSession> childRegistry,
    Set<StartingChild> startingChildren,
    SessionTopology topology,
    PauseState pauseState,
    List<RunState> runs,
    RolledBackRun lastRollback,
    ResourceGrants grants)
    implements PekkoSerializable {

  public RunResult lastResult() {
    final RunState current = currentRun();
    return current == null ? null : current.result();
  }

  public static SessionActorState initial() {
    return new SessionActorState(
        SessionState.IDLE,
        new LinkedList<>(),
        new HashMap<>(),
        new HashSet<>(),
        null,
        new PauseState(),
        new ArrayList<>(),
        null,
        ResourceGrants.EMPTY);
  }

  /** The session's currently active run, or null before its first run has ever started. */
  public RunState currentRun() {
    return runs.isEmpty() ? null : runs.getLast();
  }

  public UniqueRecord<UserMessage> currentMessage() {
    final RunState current = currentRun();
    return current == null ? null : current.message();
  }

  /**
   * One past the last committed turn's final sequence number, or the current run's own start if it
   * hasn't committed a turn yet, or 0 if no run has started yet. Derived rather than stored: it's
   * always exactly that turn's start sequence plus its event count, so keeping a separate field
   * would just be a second place for the same value to drift.
   */
  public int nextSequence() {
    final RunState current = currentRun();
    if (current == null) {
      return 0;
    }
    final CommittedTurn lastTurn = current.lastCommittedTurn();
    return lastTurn != null ? lastTurn.startSequence() + lastTurn.count() : current.startSequence();
  }

  public SessionActorState withSessionState(final SessionState sessionState) {
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState,
        runs,
        lastRollback,
        grants);
  }

  public SessionActorState withTopology(final SessionTopology updatedTopology) {
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        updatedTopology,
        pauseState,
        runs,
        lastRollback,
        grants);
  }

  public SessionActorState withNewRun(
      final UniqueRecord<UserMessage> message, final long messagePickedTimestamp) {
    final String runId = message != null ? message.getId() : null;
    final ResourceGrants incomingGrants = message != null ? message.getRecord().grants() : null;
    if (!runs.isEmpty()) {
      runs.set(runs.size() - 1, runs.getLast().finished());
    }
    runs.add(
        new RunState(runId, message, messagePickedTimestamp, nextSequence(), null, Set.of(), null));
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState,
        runs,
        lastRollback,
        grants.merge(incomingGrants));
  }

  public SessionActorState completeRun(final RunResult result) {
    runs.set(runs.size() - 1, runs.getLast().complete(result));
    return new SessionActorState(
        SessionState.IDLE,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState,
        runs,
        lastRollback,
        grants);
  }

  public SessionActorState enqueue(final UniqueRecord<UserMessage> message) {
    queue.add(message);
    return this;
  }

  public SessionActorState dequeue() {
    queue.poll();
    return this;
  }

  /**
   * Folds this turn onto the current run's tail, at the state's current {@link #nextSequence},
   * since the caller is folding this turn's commit fact onto the state that existed right before
   * the turn's events were assigned sequence numbers.
   */
  public SessionActorState withCommittedTurn(
      final int turnId, final int count, final String lastEventId) {
    final CommittedTurn newTurn = new CommittedTurn(turnId, nextSequence(), count, lastEventId);
    runs.set(runs.size() - 1, runs.getLast().withCommittedTurn(newTurn));
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState,
        runs,
        lastRollback,
        grants);
  }

  /**
   * The start sequence of {@code runId}, or null if no run with that id has ever started — the
   * sequence a rollback of that run would need, both to know where to invalidate {@code
   * SessionEvent} rows from and to fold into a {@link
   * com.agentengine.agent.core.session.events.RollbackFact}.
   */
  public Integer findRunStartSequence(final String runId) {
    for (final RunState run : runs) {
      if (Objects.equals(run.runId(), runId)) {
        return run.startSequence();
      }
    }
    return null;
  }

  /**
   * Discards {@code runId} and every run started after it, which also resets {@link #nextSequence}
   * back to where that run began, so a retried run's turns reclaim that exact sequence range. A
   * {@code runId} that never started is a no-op.
   */
  public SessionActorState withRollback(final String runId) {
    final Integer rollbackSequence = findRunStartSequence(runId);
    if (rollbackSequence == null) {
      return this;
    }
    runs.removeIf(run -> run.startSequence() >= rollbackSequence);
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState,
        runs,
        new RolledBackRun(runId, rollbackSequence),
        grants);
  }

  public Optional<ChildSession> child(final String childSessionId) {
    return Optional.ofNullable(childRegistry.get(childSessionId));
  }

  public SessionActorState startingChild(final StartingChild child) {
    startingChildren.add(child);
    return this;
  }

  public SessionActorState startedChild(final String childSessionId, final ChildSession worker) {
    childRegistry.put(childSessionId, worker);
    startingChildren.removeIf(child -> Objects.equals(child.sessionId(), childSessionId));
    return this;
  }

  public SessionActorState childStartFailed(final String sessionId) {
    startingChildren.removeIf(child -> Objects.equals(child.sessionId(), sessionId));
    return this;
  }

  public SessionActorState childPaused(final String childSessionId, final String interruptId) {
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState.withChildPaused(childSessionId, interruptId),
        runs,
        lastRollback,
        grants);
  }

  public SessionActorState selfPaused(
      final String interruptId, final String runId, final int turnId) {
    return new SessionActorState(
        SessionState.PAUSED,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState.withSelfPaused(interruptId, runId, turnId),
        runs,
        lastRollback,
        grants);
  }

  public String getPausedChild(final ResumeRequest resumeRequest) {
    return pauseState.getPausedChild(resumeRequest.getInterruptId());
  }

  public boolean isSelfInterrupt(final ResumeRequest resumeRequest) {
    return isExternalSelfInterrupt(resumeRequest)
        || pauseState.childSessionIdVsPendingInternalInterrupt().values().stream()
            .anyMatch(
                interrupt ->
                    Objects.equals(interrupt.interruptId(), resumeRequest.getInterruptId()));
  }

  public boolean isExternalSelfInterrupt(final ResumeRequest resumeRequest) {
    final String id = resumeRequest.getInterruptId();
    return pauseState.pendingExternalSelfInterrupts().containsKey(id)
        || pauseState.receivedSelfResumes().containsKey(id);
  }

  public boolean allInterruptsAnswered() {
    return CollectionUtils.isEmpty(pauseState.pendingExternalSelfInterrupts())
        && CollectionUtils.isEmpty(pauseState.childSessionIdVsPendingInternalInterrupt());
  }

  private Map<String, PauseState.TurnRef> pendingSelfInterrupts() {
    final Map<String, PauseState.TurnRef> pending =
        new HashMap<>(pauseState.pendingExternalSelfInterrupts());
    pauseState
        .childSessionIdVsPendingInternalInterrupt()
        .values()
        .forEach(interrupt -> pending.put(interrupt.interruptId(), interrupt.turnRef()));
    return pending;
  }

  /** Pending interrupt IDs whose own request event never committed to MongoDB. */
  public Set<String> orphanedSelfInterruptIds() {
    final RunState current = currentRun();
    final Set<String> orphaned = new HashSet<>();
    pendingSelfInterrupts()
        .forEach(
            (interruptId, ref) -> {
              if (current == null
                  || !Objects.equals(ref.runId(), current.runId())
                  || !current.committedTurnIds().contains(ref.turnId())) {
                orphaned.add(interruptId);
              }
            });
    return orphaned;
  }

  public SessionActorState withInterruptsDiscarded(final Collection<String> interruptIds) {
    PauseState updated = pauseState;
    for (final String interruptId : interruptIds) {
      updated = updated.withInterruptDiscarded(interruptId);
    }
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        updated,
        runs,
        lastRollback,
        grants);
  }

  public Collection<ResumeRequest> getAllReceivedResumes() {
    return pauseState.receivedSelfResumes().values();
  }

  public SessionActorState withInternalSelfPause(
      final String childSessionId, final String interruptId, final String runId, final int turnId) {
    return new SessionActorState(
        SessionState.PAUSED,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState.withInternalSelfPause(childSessionId, interruptId, runId, turnId),
        runs,
        lastRollback,
        grants);
  }

  public boolean isPausedOnExternalInterrupts() {
    return sessionState == SessionState.PAUSED
        && CollectionUtils.isNotEmpty(pauseState.pendingExternalSelfInterrupts())
        && CollectionUtils.isNotEmpty(pauseState.pendingInterruptIdVsChildSessionId());
  }

  public String getInternalInterruptId(final String childSessionId) {
    final PauseState.InternalInterrupt interrupt =
        pauseState().childSessionIdVsPendingInternalInterrupt().get(childSessionId);
    return interrupt == null ? null : interrupt.interruptId();
  }

  public SessionActorState selfResume(final ResumeRequest resumeRequest) {
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState.withSelfResumed(resumeRequest),
        runs,
        lastRollback,
        grants);
  }

  public SessionActorState childResume(final ResumeRequest resumeRequest) {
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        pauseState.withChildResumed(resumeRequest.getInterruptId()),
        runs,
        lastRollback,
        grants);
  }

  public boolean isDuplicateTurn(final Event lastTurnEvent) {
    final CommittedTurn lastTurn = lastCommittedTurn();
    if (lastTurnEvent == null || lastTurn == null) {
      return false;
    }
    return Objects.equals(lastTurn.lastEventId(), lastTurnEvent.id());
  }

  /** The current run's last committed turn, or null if it hasn't committed one yet. */
  public CommittedTurn lastCommittedTurn() {
    final RunState current = currentRun();
    return current == null ? null : current.lastCommittedTurn();
  }

  /** Whether the current run has not committed any turn of its own yet. */
  public boolean isFirstTurnOfCurrentRun() {
    return lastCommittedTurn() == null;
  }

  public SessionActorState clearSelfInterruptStates() {
    return new SessionActorState(
        sessionState,
        queue,
        childRegistry,
        startingChildren,
        topology,
        new PauseState(
            new HashMap<>(),
            new HashMap<>(),
            pauseState.pendingInterruptIdVsChildSessionId(),
            new HashMap<>()),
        runs,
        lastRollback,
        grants);
  }

  /**
   * Returns a new state with fresh copies of all mutable collections.
   *
   * <p>The per-event methods ({@code enqueue}, {@code dequeue}, {@code startingChild}, {@code
   * startedChild}, {@code withNewRun}, {@code withCommittedTurn}, {@code withRollback}) mutate
   * their backing collections in place and return {@code this} (or a record that still shares the
   * same backing collection instances) to keep event-replay O(1). As a consequence, states produced
   * by copy-style factory methods ({@code withSessionState}, etc.) share the same {@link
   * LinkedList}/{@link HashMap}/{@link HashSet}/{@link ArrayList} instances until the next in-place
   * mutation. This is safe during normal event sourcing because Pekko discards old state references
   * after each event handler returns. At snapshot boundaries, however, the state leaves the actor's
   * private domain, so fresh copies are taken here to ensure the snapshot is fully isolated from
   * any subsequent mutations.
   */
  public SessionActorState copy() {
    return new SessionActorState(
        sessionState,
        new LinkedList<>(queue),
        new HashMap<>(childRegistry),
        new HashSet<>(startingChildren),
        topology,
        pauseState,
        new ArrayList<>(runs),
        lastRollback,
        grants);
  }
}
