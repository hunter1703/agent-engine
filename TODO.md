# TODO

## Custom Actor-Based Sequential Agent Implementation (DEFERRED)

### Status: DEFERRED
The custom actor-based sequential orchestrator design is documented below but **not currently needed** since we've successfully migrated to MANAGER mode, which provides better flexibility and control.

### Original Problem Statement
The current ADK `SequentialAgent` has two critical issues:

1. **Recovery Problem**: If a sequential agent with 5 phases stops after phase 3, recovery will replay turns from phases 1-3, but the system will restart from phase 1 instead of resuming from phase 4.

2. **Terminal Event Problem**: The session actor never emits a terminal event because the `SequentialAgent` itself never emits events—only its sub-agents do. This breaks the event flow contract expected by the system.

### Proposed Solution: Actor-Based Sequential Orchestrator

Implement a custom `SequentialOrchestratorAgent` that:
- Manages phase progression explicitly in actor state
- Emits its own events (including terminal events)
- Supports proper recovery from any phase
- Maintains phase context across restarts

### Design Overview

#### 1. Core Components

**SequentialOrchestratorAgent** (extends `Agent`)
- Custom agent implementation that orchestrates sub-agents sequentially
- Does NOT delegate to ADK's `SequentialAgent`
- Emits events at orchestrator level (not just sub-agent level)
- Tracks current phase index in invocation context state

**SequentialPhaseState** (stored in `InvocationContext.state`)
```java
{
  "currentPhaseIndex": 2,           // which sub-agent we're on (0-based)
  "phaseResults": [                 // results from completed phases
    {"phase": 0, "result": "..."},
    {"phase": 1, "result": "..."}
  ],
  "originalMessage": "user input",  // initial user message
  "isComplete": false
}
```

#### 2. Execution Flow

**Initial Run:**
1. User sends message → SessionActor → SessionRunner.start()
2. SequentialOrchestratorAgent.runAsyncImpl() called
3. Extract/initialize phase state from context
4. Execute current phase sub-agent
5. Emit orchestrator event with phase completion
6. Update phase state, increment index
7. If more phases, continue; else emit terminal event

**Recovery Scenario:**
1. SessionActor recovers, finds RUNNING state
2. Replays committed events (phases 1-3)
3. SessionRunner.start("continue")
4. SequentialOrchestratorAgent reads phase state from context
5. Sees `currentPhaseIndex: 3`, skips to phase 4
6. Continues from correct phase

**Terminal Event:**
1. Last phase completes
2. SequentialOrchestratorAgent emits event with `finishReason=STOP`
3. Event has `author=orchestrator-name` (not sub-agent)
4. SessionActor sees terminal event, publishes `SessionEvent.terminal()`

#### 3. Implementation Details

**Phase Execution Pattern:**
```java
@Override
protected Flowable<Event> runAsyncImpl(InvocationContext context) {
    SequentialPhaseState state = loadOrInitState(context);
    
    if (state.isComplete()) {
        return Flowable.just(buildTerminalEvent());
    }
    
    return Flowable.defer(() -> {
        int currentPhase = state.currentPhaseIndex();
        if (currentPhase >= subAgents().size()) {
            state.markComplete();
            return Flowable.just(buildTerminalEvent());
        }
        
        BaseAgent subAgent = subAgents().get(currentPhase);
        
        return subAgent.runAsync(context)
            .concatMap(subEvent -> {
                // Wrap sub-agent events with orchestrator context
                Event wrappedEvent = wrapSubAgentEvent(subEvent, currentPhase);
                
                if (isPhaseComplete(subEvent)) {
                    state.recordPhaseResult(currentPhase, extractResult(subEvent));
                    state.incrementPhase();
                    
                    // Emit orchestrator-level phase completion event
                    Event phaseCompleteEvent = buildPhaseCompleteEvent(currentPhase);
                    
                    // Continue to next phase or complete
                    if (state.currentPhaseIndex() < subAgents().size()) {
                        return Flowable.just(wrappedEvent, phaseCompleteEvent)
                            .concatWith(runAsyncImpl(context)); // recursive for next phase
                    } else {
                        state.markComplete();
                        return Flowable.just(wrappedEvent, phaseCompleteEvent, buildTerminalEvent());
                    }
                }
                
                return Flowable.just(wrappedEvent);
            });
    });
}
```

**State Management:**
```java
private SequentialPhaseState loadOrInitState(InvocationContext context) {
    Map<String, Object> stateMap = context.state();
    String stateJson = (String) stateMap.get("sequential_phase_state");
    
    if (stateJson != null) {
        return JsonUtils.fromJson(stateJson, SequentialPhaseState.class);
    }
    
    // Initialize new state
    SequentialPhaseState newState = new SequentialPhaseState(
        0, // start at phase 0
        new ArrayList<>(),
        extractOriginalMessage(context),
        false
    );
    
    saveState(context, newState);
    return newState;
}

private void saveState(InvocationContext context, SequentialPhaseState state) {
    context.state().put("sequential_phase_state", JsonUtils.toJson(state));
}
```

**Event Building:**
```java
private Event buildPhaseCompleteEvent(int phaseIndex) {
    return Event.builder()
        .author(name()) // orchestrator name, not sub-agent
        .content(Content.text(String.format("Phase %d complete", phaseIndex + 1)))
        .turnComplete(false) // not end of turn, just phase boundary
        .build();
}

private Event buildTerminalEvent() {
    return Event.builder()
        .author(name())
        .content(Content.text("Sequential orchestration complete"))
        .finishReason(new FinishReason(FinishReason.Known.STOP))
        .turnComplete(true)
        .finalResponse(true)
        .build();
}

private Event wrapSubAgentEvent(Event subEvent, int phaseIndex) {
    // Option 1: Pass through with metadata
    return Event.builder()
        .from(subEvent)
        .metadata(Map.of(
            "orchestrator", name(),
            "phase", phaseIndex,
            "subAgent", subEvent.author()
        ))
        .build();
    
    // Option 2: Re-author to orchestrator (more invasive)
    // return Event.builder()
    //     .from(subEvent)
    //     .author(name())
    //     .build();
}
```

#### 4. Recovery Handling

The key insight: **InvocationContext.state is persisted in AgentSession.state**, which survives actor restarts.

When SessionActor recovers:
1. Loads AgentSession from MongoDB
2. AgentSession.state contains `sequential_phase_state`
3. SessionRunner creates new Runner with restored state
4. SequentialOrchestratorAgent.runAsyncImpl() reads phase state
5. Skips completed phases, continues from current phase

**Critical**: Phase state must be updated **before** emitting phase completion event, so if actor crashes after event but before state update, we replay the phase (idempotent).

#### 5. Integration Points

**OrchestratorAgentFactory:**
```java
private DelegatedAgent buildSequential(BaseAgentConfig config, List<? extends Agent> subAgents) {
    if (CollectionUtils.isEmpty(subAgents)) {
        throw new IllegalArgumentException(
            "orchestrator mode SEQUENTIAL requires non-empty subAgentIds for agent_id=" + config.getId());
    }
    
    // Replace ADK SequentialAgent with custom implementation
    return new SequentialOrchestratorAgent(
        config.getId(),
        config.getDescription(),
        subAgents,
        config
    );
}
```

**SequentialOrchestratorAgent:**
```java
public class SequentialOrchestratorAgent extends Agent {
    
    public SequentialOrchestratorAgent(
            String name,
            String description,
            List<? extends BaseAgent> subAgents,
            BaseAgentConfig config) {
        super(name, description, subAgents, config, null, null);
    }
    
    @Override
    protected Flowable<Event> runAsyncImpl(InvocationContext context) {
        // Implementation as described above
    }
    
    @Override
    protected Flowable<Event> runLiveImpl(InvocationContext context) {
        // Similar to runAsyncImpl but with streaming
        return runAsyncImpl(context);
    }
}
```

#### 6. Testing Strategy

**Unit Tests:**
- `SequentialOrchestratorAgentTest.shouldExecutePhasesInOrder()`
- `SequentialOrchestratorAgentTest.shouldEmitTerminalEventAfterLastPhase()`
- `SequentialOrchestratorAgentTest.shouldPreservePhaseStateInContext()`

**Integration Tests:**
- `SequentialOrchestratorIT.shouldRecoverFromPhase3AndContinue()`
  - Start 5-phase orchestrator
  - Stop after phase 3
  - Restart actor
  - Verify continues from phase 4
  - Verify terminal event emitted

- `SequentialOrchestratorIT.shouldEmitTerminalEventToSessionActor()`
  - Run complete orchestration
  - Verify SessionActor receives terminal event
  - Verify SessionEvent.terminal() published

#### 7. Migration Path

1. Implement `SequentialOrchestratorAgent` alongside existing `SequentialAgentBuilder`
2. Add feature flag: `orchestrator.sequential.use_custom=true`
3. Update `OrchestratorAgentFactory.buildSequential()` to check flag
4. Test with existing sequential agents
5. Once stable, remove ADK `SequentialAgent` dependency
6. Remove feature flag, make custom implementation default

#### 8. Advantages Over ADK SequentialAgent

| Aspect | ADK SequentialAgent | Custom SequentialOrchestratorAgent |
|--------|---------------------|-------------------------------------|
| Recovery | Replays all phases, restarts from phase 1 | Resumes from last completed phase |
| Terminal Events | Never emits (only sub-agents do) | Emits terminal event after last phase |
| Phase Tracking | Implicit in ADK internals | Explicit in actor state |
| Debugging | Black box | Full visibility into phase state |
| Customization | Limited | Full control over orchestration logic |
| Event Authorship | Sub-agents only | Orchestrator + sub-agents |

#### 9. Open Questions

1. **Phase Context Passing**: Should each phase receive results from previous phases?
   - Option A: Pass in message content
   - Option B: Store in shared context state
   - **Recommendation**: Option B (already in state)

2. **Partial Phase Failure**: What if phase 3 fails mid-execution?
   - Current: SessionActor handles via RunFailedCommand
   - Proposed: Same, but phase state shows which phase failed
   - **Recommendation**: Add `failedPhase` field to state

3. **Parallel Sub-Phases**: Should we support parallel execution within a phase?
   - **Recommendation**: No, use separate ParallelOrchestratorAgent for that

4. **Phase Naming**: Should phases have names beyond indices?
   - **Recommendation**: Yes, add optional `phaseNames` config
   - Example: `["analyze", "plan", "execute", "verify", "report"]`

#### 10. Implementation Checklist

- [ ] Create `SequentialPhaseState` record
- [ ] Implement `SequentialOrchestratorAgent`
- [ ] Add state serialization/deserialization
- [ ] Implement phase execution loop
- [ ] Add terminal event emission
- [ ] Update `OrchestratorAgentFactory`
- [ ] Write unit tests
- [ ] Write integration tests (with actor restart)
- [ ] Test recovery scenarios
- [ ] Verify terminal event propagation
- [ ] Add phase naming support (optional)
- [ ] Document usage in agent config
- [ ] Migration guide for existing sequential agents

### Alternative Approaches Considered

**Alternative 1: Patch ADK SequentialAgent**
- Pros: Minimal code changes
- Cons: Don't control ADK internals, may break on updates
- **Rejected**: Too fragile

**Alternative 2: Wrapper Around ADK SequentialAgent**
- Pros: Reuse ADK logic
- Cons: Still can't fix recovery or terminal event issues
- **Rejected**: Doesn't solve core problems

**Alternative 3: State Machine in SessionActor**
- Pros: Full control at actor level
- Cons: Mixes orchestration logic with session management
- **Rejected**: Violates separation of concerns

**Selected: Custom Agent Implementation**
- Pros: Clean separation, full control, proper event flow
- Cons: More code to maintain
- **Accepted**: Best long-term solution

### References

- SessionActor: `agent-engine/runtime/src/main/java/com/agentengine/runtime/session/SessionActor.java`
- DelegatedAgent: `agent-engine/runtime/src/main/java/com/agentengine/runtime/agents/DelegatedAgent.java`
- SequentialAgentBuilder: `agent-engine/runtime/src/main/java/com/agentengine/runtime/factories/agent/builders/SequentialAgentBuilder.java`
- OrchestratorAgentFactory: `agent-engine/runtime/src/main/java/com/agentengine/runtime/factories/agent/OrchestratorAgentFactory.java`
