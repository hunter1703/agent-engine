# Agent Engine Protocol Specification (RFC-2026-03)

## Status of this Memo

This document specifies an exhaustive technical protocol for the Agent Engine (`DefaultFlow`) and its primary UI mapping layer (`AGUIEventMapper`). It defines strict behavioral guarantees, state transitions, and event-ordering constraints required for system integrity and consistent user experience.

---

## 1. Introduction

The Agent Engine operates as a stateful, pull-based execution pipeline. Every interaction is governed by a series of Request and Response Processors that enforce protocol hygiene, structural normalization, and behavioral constraints.

---

## 2. Terminology

- **Run**: A logical unit of work identified by a unique `invocationId`.
- **Turn**: A single request-response cycle within a Run.
- **Part**: The fundamental unit of content in an `LlmResponse` (Text, Thought, Tool Call, Tool Response).
- **Violation**: A protocol-level error flagged by a processor, often triggering a correction nudge to the model.
- **Phase**: The current logical state of a Run (UNKNOWN, REASONING, FINISHED).

---

## 3. Core Engine Protocol (`DefaultFlow`)

The `DefaultFlow` is the definitive source of truth for the interaction protocol. It guarantees the following sequential behaviors:

### 3.1. Content Normalization and Hygiene (`Parser`)

- **Tag Parsing**: Raw `<thought>` or `[reasoning]` tags are converted into structured `Part` objects.
- **Partial Response Constraints**:
  - **Guarantee**: Tool calls and responses are STRIPPED from partial (streaming) responses.
  - **Reasoning**: Partial tool calls are ambiguous; the engine only executes tools from finalized, non-partial turns.
  - **Violation**: `partial_tool_calls` is emitted if tools are detected in a streaming chunk.

### 3.2. Structural Integrity (`PartOrderingResponseProcessor`)

- **Canonical Ordering**: All parts within an `LlmResponse` MUST be sorted as follows:
  1. **Thoughts** (`thought == true`)
  2. **Standard Text**
  3. **Tool Calls**
  4. **Tool Responses**
- **Guarantee**: Downstream consumers (Mappers, UI) can rely on this order for sequential event generation.

### 3.3. Behavioral Constraints (`Processors`)

- **Redundancy Protection (`RedundantToolCallsResponseProcessor`)**:
  - **Guarantee**: If a model repeats a tool-call sequence (name + args) identical to the previous turn, the sequence is stripped and a `redundant_tool_calls` violation is emitted.
- **Plan Enforcement (`PlanLoopResponseProcessor`)**:
  - **Guarantee**: Natural termination by text is rejected if the current Plan has open tasks. Violation: `incomplete_task` or `final_answer_validation`.
  - **Sanitization**: If the turn indicates a premature final answer (text only) while tasks are incomplete, the text is converted to a thought.

### 3.4. Lifecycle and Termination (`RunCleanupResponseProcessor` & `TurnCompletionResponseProcessor`)

- **Positive Completion Guarantees**:
  - **Tool Invocation**: If a non-partial response contains valid (non-stripped) tool calls, the engine forces `turnComplete: true`.
  - **Model Stop**: If the model signals `finishReason: STOP` and no violations are present, the engine forces `turnComplete: true`.
  - **Natural Termination**: If a run ends with a text message and no pending tools, `turnComplete` is forced to `true`.
- **Terminal Signals**:
  - **Finish Reason**: If the Run is truly finished (turn is complete and no tools were called), the engine guarantees `finishReason: STOP`.
  - **Completion Flags**: Sets `partial: false` and `turnComplete: true` on the final response.
- **Cleanup**: Clears transient `RunState` fields while preserving durable plan state in session context.
- **HITL Pause/Resume**:
  - Calling `user_clarification` pauses the session (`hitl.paused=true`) and halts flow continuation after the current turn.
  - The next user message on the same session is treated as the resume signal; pause state is cleared and the answer is injected as resume context.

### 3.5. Processor Hygiene and State Handling

- **TurnComplete Usage**:
  - **Guarantee**: Response processors MUST NOT base their logic on the current value of `turnComplete`.
  - **Reasoning**: `turnComplete` is a lifecycle signal for the agentic loop, not for intermediate processing. Processors may *set* or *flip* this flag, but should never *read* it to branch logic.
  - **Ownership**: The final `LogicalCompletionResponseProcessor` is the definitive authority on positive completion signals.

### 3.6. Multi-Agent Delegation and Handoff

- **Manager-as-Tool Delegation** (`delegate_to_agent`):
  - **Guarantee**: A source agent can call another agent as a tool and receive the delegated agent's visible text output summarized back into the tool result.
  - **Session Rule**: Delegation uses a derived target session id (`<source>::delegate::<target>-<uuid>`) unless configured otherwise.
- **Decentralized Handoff** (`handoff_to_agent`):
  - **Guarantee**: Handoff metadata is written into source session state (`handoff.*`) and consumed exactly once by runtime orchestration.
  - **Runtime Continuation**: After source run completion, `AgentExecutionServiceImpl` checks handoff state; when present, it starts a follow-up run on the target agent with the handoff message and next session id, then clears handoff keys.
  - **Loop Protection**: Handoff continuation enforces a max-hop limit; exceeding limit blocks continuation and increments telemetry (`handoff_loop_blocked`).
- **Parallelization Workflow** (`parallel_delegate`):
  - **Guarantee**: Parallel delegated branches can run in `SECTIONING` or `VOTING` mode.
  - **Aggregation Policies**: `CONCATENATE`, `BEST_EFFORT`, `MAJORITY_VOTE`.
  - **Stopping Policies**: `ALL_COMPLETE`, `FIRST_SUCCESS`, `QUORUM`.

### 3.7. Evaluator-Optimizer Workflow

- **Evaluator Stage** (`EvaluatorOptimizerResponseProcessor`):
  - **Guarantee**: Final text responses are scored against a topic anchor and may be retried before completion.
  - **Stopping Policy**: `RETRY_THEN_ALLOW` or `RETRY_THEN_BLOCK` after retry limit.
  - **Loop Behavior**: Low-scoring responses generate correction violations and force another turn (`turnComplete=false`) until threshold or stop policy is reached.

---

## 4. AGUI Event Protocol (`AGUIEventMapper`)

The `AGUIEventMapper` translates ADK engine signals into a structured UI event stream.

### 4.1. Lifecycle Guarantees

- **Step Management**:
  - **Auto-Start**: A `StepStartedEvent` is implicitly emitted at the start of any turn if no step is active.
  - **Auto-Finish**: A `StepFinishedEvent` is guaranteed when the engine signals `turnComplete: true`.
- **Run Finalization**:
  - **Guarantee**: `RunFinishedEvent` is emitted only via `onComplete()`, carrying the captured `finalAnswer`.

### 4.2. Visual Component Protocols

- **Thinking Bubble Lifecycle**:
  - **Entry**: `ThinkingStartEvent` + `ThinkingTextMessageStartEvent` are triggered by the first thought part.
  - **Exit**: Implicitly closed by `closeThinkingIfNeeded()` before any Text or Tool Call parts are processed.
  - **Hygiene**: Blank or whitespace-only thoughts are ignored to prevent empty bubbles.
- **Text Message Reconstruction**:
  - **Streaming**: Chunked text is buffered and emitted via `TextMessageChunkEvent`.
  - **Completeness**: A final `TextMessageContentEvent` with the full reconstructed text is guaranteed on `partial: false`.

### 4.3. Metadata and Decoration

- **RFC Compliance**: Every event emitted by the mapper MUST include:
  - `agentId`, `sessionId`, `timestamp`.
  - `runId` and `threadId`.
  - Detailed `rawEvent` mapping for protocol auditing.

---

## 5. Violation Codes

- `partial_tool_calls`: Tool parts found in a streaming response.
- `redundant_tool_calls`: Repeating tool calls with identical arguments.
- `premature_termination`: Ending a turn without a required action signal.
- `incomplete_task`: Attempting to finish while tasks are still open.
- `final_answer_validation`: General plan/task validation failure.
