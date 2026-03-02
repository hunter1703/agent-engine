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
- **Phase**: The current logical state of a Run (REASONING, READY_FOR_FINAL_ANSWER, etc.).

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
- **Final Answer Protocol (`FinalAnswerResponseProcessor`)**:
  - **Simultaneity Guard**: Simultaneous regular text and `submit_final_answer` tool calls are forbidden. Violation: `final_answer_protocol`.
  - **Sanitization**: If ANY tool call (execution or final) is present (even if partial), all regular text MUST be converted to thoughts.
  - **Nudge**: Concluding a turn without a tool call or final answer signal while in `REASONING` phase triggers a `premature_termination` violation.
- **Plan Enforcement (`PlanLoopResponseProcessor`)**:
  - **Guarantee**: The `submit_final_answer` tool is rejected if the current Plan has open tasks. Violation: `incomplete_task` or `final_answer_validation`.

### 3.4. Lifecycle and Termination (`RunCleanupResponseProcessor` & `LogicalCompletionResponseProcessor`)

- **State Transition**: Advances the Run phase from `FINAL_ANSWER_DELIVERED` to `FINISHED`.
- **Positive Completion Guarantees**:
  - **Tool Invocation**: If a non-partial response contains valid (non-stripped) tool calls, the engine forces `turnComplete: true`.
  - **Model Stop**: If the model signals `finishReason: STOP` and no violations are present, the engine forces `turnComplete: true`.
  - **Final Answer**: Once `FINAL_ANSWER_DELIVERED` is reached, `turnComplete` is forced to `true`.
- **Terminal Signals**:
  - **Finish Reason**: If the Run reaches `FINISHED`, the engine guarantees `finishReason: STOP`.
  - **Completion Flags**: Sets `partial: false` and `turnComplete: true` on the final response.
- **Guarantee**: If the Run phase is NOT `FINAL_ANSWER_DELIVERED`, the engine STRIPS any `finishReason` to avoid ambiguous termination.
- **Cleanup**: Purges the `RunState` from the session context upon completion.

### 3.5. Processor Hygiene and State Handling

- **TurnComplete Usage**:
  - **Guarantee**: Response processors MUST NOT base their logic on the current value of `turnComplete`.
  - **Reasoning**: `turnComplete` is a lifecycle signal for the agentic loop, not for intermediate processing. Processors may *set* or *flip* this flag, but should never *read* it to branch logic.
  - **Ownership**: The final `LogicalCompletionResponseProcessor` is the definitive authority on positive completion signals.

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
- `final_answer_protocol`: Simultaneous text and final answer.
- `premature_termination`: Ending a turn without a required action signal.
- `incomplete_task`: Attempting to finish while tasks are still open.
- `final_answer_validation`: General plan/task validation failure.
