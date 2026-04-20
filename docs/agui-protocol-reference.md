# AGUI Protocol Reference

Complete reference for the AG-UI event streaming protocol. Covers every event type, all
sequencing rules, conformance requirements, producer obligations, and consumer guarantees.
Written so that reading this document alone is sufficient to implement a correct producer
or consumer, audit an event stream for conformance, or reason about edge cases.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Transport and Wire Format](#2-transport-and-wire-format)
3. [Base Event Properties](#3-base-event-properties)
4. [Run Lifecycle Events](#4-run-lifecycle-events)
5. [Step Events](#5-step-events)
6. [Text Message Events](#6-text-message-events)
7. [Tool Call Events](#7-tool-call-events)
8. [Reasoning Events](#8-reasoning-events)
9. [State Management Events](#9-state-management-events)
10. [Activity Events](#10-activity-events)
11. [Special and Meta Events](#11-special-and-meta-events)
12. [Custom Events](#12-custom-events)
13. [Convenience (Chunk) Events](#13-convenience-chunk-events)
14. [Concurrency Rules](#14-concurrency-rules)
15. [Complete Conformance Rules](#15-complete-conformance-rules)
16. [Valid Stream Examples](#16-valid-stream-examples)
17. [Invalid Stream Examples](#17-invalid-stream-examples)
18. [Producer Checklist](#18-producer-checklist)
19. [Consumer Requirements](#19-consumer-requirements)
20. [Deprecated Events](#20-deprecated-events)
21. [Implementation Notes and Quirks](#21-implementation-notes-and-quirks)

---

## 1. Overview

AG-UI is a **streaming event protocol** for communicating agent execution state from a server
(the agent runtime) to a client (a UI or downstream consumer). It is designed around a
**run-scoped event stream**: every agent invocation is a run, and the stream carries a
strictly ordered sequence of typed events that together describe what happened.

### Core design principles

- **Ordered delivery**: events within a run have a defined ordering. Consumers process them
  in arrival order. Producers must emit them in the correct order.
- **Paired lifecycle events**: most constructs (runs, steps, messages, tool calls, reasoning)
  use Start/End pairs. Every Start must have a corresponding End.
- **Streaming-first**: text content, tool arguments, and reasoning content are delivered as
  incremental deltas. The full content is the concatenation of deltas in order.
- **Explicit IDs link related events**: `messageId`, `toolCallId`, `runId`, and `stepName` are
  the binding keys. All events for the same logical entity share the same ID.
- **Extensible via Custom**: application-specific signals are carried as `CUSTOM` events with
  a `name` discriminator and `value` payload.

### What a run looks like at a glance

```
RunStarted
  [StepStarted
    [ReasoningStart ... ReasoningEnd]   (optional, before or after text)
    [TextMessageStart ... TextMessageEnd]
    [ToolCallStart ... ToolCallEnd]
    [ToolCallResult]
  StepFinished]
  ... (more steps)
RunFinished | RunError
```

---

## 2. Transport and Wire Format

AG-UI is transport-agnostic but primarily used over **Server-Sent Events (SSE)**. Each SSE
`data:` payload is a JSON-serialized event object. The `type` field in the JSON payload
identifies the event type.

```
event: message
data: {"type":"RunStarted","runId":"run-abc","threadId":"thread-xyz","timestamp":1742400000000}

event: message
data: {"type":"TextMessageStart","messageId":"msg-1","role":"assistant","timestamp":1742400000100}
```

The SSE `event:` field is typically `message` regardless of the AGUI event type — the type
discrimination happens inside the JSON `type` field.

---

## 3. Base Event Properties

Every event has these properties:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | **Yes** | Event type identifier (see each section) |
| `timestamp` | number | No | Unix epoch milliseconds |
| `rawEvent` | any | No | Original upstream event if this was transformed from another format |

`rawEvent` is populated by producers that translate from another event format (e.g., ADK events
mapped to AGUI). Consumers may use it for debugging or to access fields not surfaced at the
typed level.

---

## 4. Run Lifecycle Events

A **run** is one agent invocation, identified by a unique `runId`. It always begins with
`RunStarted` and ends with exactly one of `RunFinished` or `RunError`. All other events in the
stream belong to the run they appear after in.

### 4.1 `RunStarted`

Signals the beginning of an agent run.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"RunStarted"` | **Yes** | |
| `runId` | string | **Yes** | Unique ID for this run |
| `threadId` | string | **Yes** | Conversation thread this run belongs to |
| `parentRunId` | string | No | Parent run ID for subagent/nested runs (see §14.4) |
| `input` | object | No | Agent input payload. May omit messages already present in history. |

**Rules:**
- Must be the **first** event emitted for a run.
- `runId` must be unique across all runs in the thread.
- `threadId` is stable across runs — it is the session/conversation identifier.
- If this is a subagent run spawned within a parent orchestration, `parentRunId` must be set
  to the orchestrator's `runId`. This creates the run lineage tree.

### 4.2 `RunFinished`

Signals successful completion of a run.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"RunFinished"` | **Yes** | |
| `runId` | string | **Yes** | Must match the corresponding `RunStarted.runId` |
| `threadId` | string | **Yes** | |
| `result` | any | No | Final output data produced by the agent |
| `outcome` | `"success"` \| `"interrupt"` | No | *(draft)* How the run ended |
| `interrupt` | object | No | *(draft)* Interrupt details when `outcome="interrupt"` |

**Rules:**
- Must be the **last** event of a run when the run ends successfully.
- No further events for this run may appear after `RunFinished`.
- `outcome="interrupt"` signals the run was paused (e.g., HITL) and expects a resume signal.
  When interrupted, the `interrupt` field carries the pause reason, prompt, and options.
- When `outcome` is absent, consumers should treat it as `"success"`.

### 4.3 `RunError`

Signals an unrecoverable error.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"RunError"` | **Yes** | |
| `message` | string | **Yes** | Human-readable error description |
| `code` | string | No | Machine-readable error code |

**Rules:**
- Must be the **last** event of a run when the run ends in error.
- No further events for this run may appear after `RunError`.
- `RunError` and `RunFinished` are mutually exclusive — exactly one closes a run.

---

## 5. Step Events

A **step** is a named subunit of a run. Steps are optional but strongly recommended for
complex agent executions — they give the UI structure to display (e.g., "Planning...",
"Executing tool...").

### 5.1 `StepStarted`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"StepStarted"` | **Yes** | |
| `stepName` | string | **Yes** | Name of this step; must be unique within the run |

### 5.2 `StepFinished`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"StepFinished"` | **Yes** | |
| `stepName` | string | **Yes** | Must exactly match the corresponding `StepStarted.stepName` |

**Rules:**
- `StepStarted` must always have a corresponding `StepFinished` with the **same** `stepName`.
- `StepFinished` must come after `StepStarted` with the same name.
- Multiple `StepStarted`/`StepFinished` pairs per run are allowed.
- The spec does **not** define nested steps. Steps are flat within a run.
- All in-progress messages, tool calls, and reasoning within a step must be explicitly closed
  before `StepFinished` is emitted. A step that ends with an unclosed text message is non-conformant.
- Steps are optional. A run may contain events without any `StepStarted`/`StepFinished`.

---

## 6. Text Message Events

A **text message** is a streaming assistant (or user/system) message delivered as an ordered
sequence of delta chunks. The full message content is the concatenation of all `delta` values
from `TextMessageContent` events in order.

### 6.1 `TextMessageStart`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"TextMessageStart"` | **Yes** | |
| `messageId` | string | **Yes** | Unique ID for this message within the conversation |
| `role` | `"assistant"` \| `"user"` \| `"system"` \| `"developer"` \| `"tool"` | **Yes** | Speaker role |

### 6.2 `TextMessageContent`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"TextMessageContent"` | **Yes** | |
| `messageId` | string | **Yes** | Must match the corresponding `TextMessageStart.messageId` |
| `delta` | string | **Yes** | Non-empty text chunk |

**Rule:** `delta` must be **non-empty**. Zero-length deltas are non-conformant.

### 6.3 `TextMessageEnd`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"TextMessageEnd"` | **Yes** | |
| `messageId` | string | **Yes** | Must match the corresponding `TextMessageStart.messageId` |

**Full text message sequence:**
```
TextMessageStart(messageId=M, role=assistant)
TextMessageContent(messageId=M, delta="Hello")
TextMessageContent(messageId=M, delta=", world")
TextMessageEnd(messageId=M)
```

**Rules:**
- `TextMessageStart` must precede any `TextMessageContent` or `TextMessageEnd` with the same `messageId`.
- Zero or more `TextMessageContent` events may appear between Start and End. A message with no
  content (empty message) is allowed: `Start → End` with no `Content` events.
- `TextMessageEnd` must follow `TextMessageStart` for the same `messageId`.
- No `TextMessageContent` or additional `TextMessageStart` for the same `messageId` may appear
  after `TextMessageEnd`.
- Multiple messages with **different** `messageId`s may be open simultaneously (see §14.1).

**Streaming vs. non-streaming pattern:**

For streaming (token by token): emit one `TextMessageContent` per token or chunk.

For non-streaming (full text at once): emit one `TextMessageContent` with the complete text,
then `TextMessageEnd`. This is also the correct pattern when closing a streaming message — emit
a final `TextMessageContent` with the full accumulated text as a sync signal before the End.

---

## 7. Tool Call Events

A **tool call** describes one invocation of an external tool by the agent. Arguments are
delivered as streaming deltas (typically JSON fragments). The result is a separate event
delivered after execution completes.

### 7.1 `ToolCallStart`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ToolCallStart"` | **Yes** | |
| `toolCallId` | string | **Yes** | Unique ID for this tool invocation |
| `toolCallName` | string | **Yes** | Name of the tool being called |
| `parentMessageId` | string | No | Step name or message ID that triggered this call |

**Note on `parentMessageId`:** Optional per spec but semantically important for UI attribution.
Set it to the current step name so the UI can link the tool call to its originating step.

### 7.2 `ToolCallArgs`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ToolCallArgs"` | **Yes** | |
| `toolCallId` | string | **Yes** | Must match the corresponding `ToolCallStart.toolCallId` |
| `delta` | string | **Yes** | Argument chunk, typically a JSON fragment |

### 7.3 `ToolCallEnd`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ToolCallEnd"` | **Yes** | |
| `toolCallId` | string | **Yes** | Must match the corresponding `ToolCallStart.toolCallId` |

### 7.4 `ToolCallResult`

Delivers the result of tool execution. Structurally separate from `ToolCallEnd` — `End` closes
the argument stream; `Result` delivers the execution output. They are distinct events.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ToolCallResult"` | **Yes** | |
| `toolCallId` | string | **Yes** | Links to the tool call that produced this result |
| `messageId` | string | **Yes** | Conversation message this result belongs to |
| `content` | string | **Yes** | Result content (typically JSON-encoded) |
| `role` | `"tool"` | No | Typically `"tool"` |

**Note on `messageId`:** Links the result back to the conversation message context. Should be
set to the step or message ID that originated the tool call (the same value as
`ToolCallStart.parentMessageId`).

**Full tool call sequence:**
```
ToolCallStart(toolCallId=tc1, toolCallName=web_search, parentMessageId=step-1)
ToolCallArgs(toolCallId=tc1, delta='{"query":')
ToolCallArgs(toolCallId=tc1, delta='"AAPL stock"}')
ToolCallEnd(toolCallId=tc1)
... (tool executes) ...
ToolCallResult(toolCallId=tc1, messageId=step-1, content='{"price":185.92}')
```

**Rules:**
- `ToolCallStart` must precede any `ToolCallArgs` or `ToolCallEnd` with the same `toolCallId`.
- `ToolCallEnd` closes the argument stream. `ToolCallResult` may arrive at any time after
  `ToolCallStart`, including after `ToolCallEnd`.
- `ToolCallResult` is NOT required immediately after `ToolCallEnd`. It arrives when execution
  completes, which may be after the agent continues to the next step.
- Multiple tool calls with different `toolCallId`s may be open simultaneously (see §14.2).
- The full arguments are the concatenation of all `delta` values in order.

---

## 8. Reasoning Events

**Reasoning** represents the agent's internal thinking process (chain-of-thought). It is
structured as a two-level hierarchy: an outer reasoning group (`ReasoningStart`/`ReasoningEnd`)
containing one or more reasoning message blocks (`ReasoningMessageStart`/`ReasoningMessageEnd`).

The outer `messageId` identifies the **reasoning group**. The inner `messageId` identifies an
individual **thought block** within the group. These are different IDs.

### 8.1 `ReasoningStart`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningStart"` | **Yes** | |
| `messageId` | string | **Yes** | Unique ID for this reasoning group |

### 8.2 `ReasoningMessageStart`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningMessageStart"` | **Yes** | |
| `messageId` | string | **Yes** | Unique ID for this thought block (NOT the same as the outer reasoning `messageId`) |
| `role` | `"assistant"` | **Yes** | Always `"assistant"` for reasoning messages |

### 8.3 `ReasoningMessageContent`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningMessageContent"` | **Yes** | |
| `messageId` | string | **Yes** | Must match the corresponding `ReasoningMessageStart.messageId` |
| `delta` | string | **Yes** | Non-empty thought content chunk |

**Rule:** `delta` must be **non-empty**.

### 8.4 `ReasoningMessageEnd`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningMessageEnd"` | **Yes** | |
| `messageId` | string | **Yes** | Must match the corresponding `ReasoningMessageStart.messageId` |

### 8.5 `ReasoningEnd`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningEnd"` | **Yes** | |
| `messageId` | string | **Yes** | Must match the corresponding `ReasoningStart.messageId` |

### 8.6 `ReasoningEncryptedValue` *(draft)*

Attaches an opaque encrypted blob to a message or tool call, preserving chain-of-thought for
the agent's internal use without exposing raw reasoning content. Used under zero data retention
(ZTR) policies — the client stores the blob opaquely and forwards it back to the agent on
the next turn; only the agent can decrypt it.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningEncryptedValue"` | **Yes** | |
| `subtype` | `"message"` \| `"tool-call"` | **Yes** | Whether this attaches to a message or a tool call |
| `entityId` | string | **Yes** | `messageId` (if `subtype="message"`) or `toolCallId` (if `subtype="tool-call"`) |
| `encryptedValue` | string | **Yes** | Opaque encrypted blob; client must not inspect or modify |

**Full reasoning sequence (two thought blocks):**
```
ReasoningStart(messageId=reasoning-1)
  ReasoningMessageStart(messageId=block-1, role=assistant)
  ReasoningMessageContent(messageId=block-1, delta="I need to consider...")
  ReasoningMessageContent(messageId=block-1, delta=" the user's intent first.")
  ReasoningMessageEnd(messageId=block-1)

  ReasoningMessageStart(messageId=block-2, role=assistant)
  ReasoningMessageContent(messageId=block-2, delta="The best approach is...")
  ReasoningMessageEnd(messageId=block-2)
ReasoningEnd(messageId=reasoning-1)
```

**Rules:**
- `ReasoningStart` must precede all `ReasoningMessage*` events in the group.
- `ReasoningEnd` must close `ReasoningStart` with the **same** outer `messageId`.
- `ReasoningMessageStart` must precede `ReasoningMessageContent` and `ReasoningMessageEnd`
  for the same inner `messageId`.
- `ReasoningMessageEnd` must come before the next `ReasoningMessageStart` within the same group.
- After `ReasoningEnd`, no further reasoning events with the same outer `messageId` are allowed.
- The outer `messageId` and inner `messageId`s must be distinct values.
- Multiple reasoning blocks (inner) may exist within one group (outer).

---

## 9. State Management Events

State events synchronize the agent's internal state to the client. They carry arbitrary
JSON state objects (not conversation messages).

### 9.1 `StateSnapshot`

Replaces the **entire** current state with the provided snapshot.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"StateSnapshot"` | **Yes** | |
| `snapshot` | object | **Yes** | Complete state object. **Replaces**, not merges, current state. |

### 9.2 `StateDelta`

Applies incremental updates to the current state.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"StateDelta"` | **Yes** | |
| `delta` | array | **Yes** | Array of **RFC 6902 JSON Patch** operations, applied in order |

**RFC 6902 JSON Patch operations:** `add`, `remove`, `replace`, `move`, `copy`, `test`.

**Rules:**
- `StateSnapshot` is the authoritative reset — always replaces, never merges.
- `StateDelta` patches are applied sequentially; order matters.
- If the client detects inconsistency after applying a `StateDelta` (e.g., patch target not found),
  it may request a fresh `StateSnapshot` to resynchronize.
- `StateSnapshot` serves as a synchronization checkpoint. A producer should emit a `StateSnapshot`
  at run start (if state is non-empty) and after significant state changes.
- State is separate from conversation messages. It is not part of the agent's LLM context.

### 9.3 `MessagesSnapshot`

Replaces the client's view of the conversation message history.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"MessagesSnapshot"` | **Yes** | |
| `messages` | array | **Yes** | Complete array of message objects |

---

## 10. Activity Events

Activity events carry structured side-channel output that is distinct from the main
conversation — things like plans, search results, code artifacts, or any rich structured data
the agent produces. Activity is associated with a specific `messageId` and an `activityType`.

### 10.1 `ActivitySnapshot`

Creates or replaces a structured activity output.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ActivitySnapshot"` | **Yes** | |
| `messageId` | string | **Yes** | Message this activity is associated with |
| `activityType` | string | **Yes** | Application-defined type identifier (e.g., `"PLAN"`, `"SEARCH"`, `"CODE"`) |
| `content` | object | **Yes** | Structured JSON content of the activity |
| `replace` | boolean | No | Default `true`. If `false`, ignore this event if an activity for this `messageId` already exists. |

### 10.2 `ActivityDelta`

Incrementally updates an existing activity.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ActivityDelta"` | **Yes** | |
| `messageId` | string | **Yes** | Must reference an activity previously established by `ActivitySnapshot` |
| `activityType` | string | **Yes** | Must match the `activityType` of the existing activity |
| `patch` | array | **Yes** | Array of **RFC 6902 JSON Patch** operations applied to `content` |

**Rules:**
- `ActivityDelta` requires a prior `ActivitySnapshot` for the same `messageId`. It is an error
  to emit `ActivityDelta` with no preceding `ActivitySnapshot`.
- If the client detects divergence (patch target not found), it may request a fresh
  `ActivitySnapshot` to resynchronize.
- `replace=false` on `ActivitySnapshot` acts as a "create if not exists" — useful when the
  producer is not sure if the client already has a snapshot.

---

## 11. Special and Meta Events

### 11.1 `Raw`

Container for events from external systems that are being passed through without transformation.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"Raw"` | **Yes** | |
| `event` | any | **Yes** | The original event data, preserved as-is |
| `source` | string | No | Identifier of the originating system |

### 11.2 `MetaEvent` *(draft)*

A side-band annotation that can appear anywhere in the stream without affecting the primary
event flow. Used for adding application-specific metadata to the stream.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"MetaEvent"` | **Yes** | |
| `metaType` | string | **Yes** | Application-defined type (e.g., `"thumbs_up"`, `"tag"`, `"diagnostic"`) |
| `payload` | any | **Yes** | Structured metadata |

**Rules:**
- `MetaEvent` may appear at any point in the stream — before, after, or between any other events.
- It does not affect run lifecycle or sequencing constraints.
- It should not carry information that is required for the run to make sense — it is purely
  additive annotation.

---

## 12. Custom Events

`Custom` events carry application-specific signals not defined in the core protocol.

### 12.1 `Custom`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"Custom"` | **Yes** | Wire type is always `"Custom"` |
| `name` | string | **Yes** | Discriminator identifying the specific custom event kind |
| `value` | any | **Yes** | Payload associated with this custom event |

**Rules:**
- The `name` field is the discriminator — consumers switch on `name` to handle specific custom types.
- The `value` field contains the payload. Its shape is defined per-`name` by the application.
- Custom events must be documented by the team using them to ensure consistent handling.
- Custom events should follow the naming pattern of core events (start/end pairs if they have lifecycle).

**Example — a correction event:**
```json
{
  "type": "Custom",
  "name": "correction",
  "value": {
    "correctionType": "OUTPUT_RELEVANCE",
    "code": "IRRELEVANT_RESPONSE",
    "message": "Response does not address the user's question"
  }
}
```

**Note:** Our implementation currently puts individual fields at the top level of the Custom event
rather than nesting them inside `value`. This works because the client reads from `rawEvent` which
contains all serialized fields, but it deviates from the strict spec shape. Producers should
migrate to wrapping payload in `value` for spec compliance.

---

## 13. Convenience (Chunk) Events

Convenience events are shorthand that auto-expand into their corresponding Start/Content/End
sequences. They are useful for producers that don't want to manage lifecycle state.

### 13.1 `TextMessageChunk`

Auto-expands to `TextMessageStart → TextMessageContent → TextMessageEnd`.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"TextMessageChunk"` | **Yes** | |
| `messageId` | string | **Yes** | Message ID |
| `role` | string | **On first chunk** | Defaults to `"assistant"` if not provided on first chunk |
| `delta` | string | No | Text content |

**Auto-close behavior:** The consumer auto-emits `TextMessageEnd` when:
- A new `messageId` appears on a subsequent `TextMessageChunk`
- The stream ends

### 13.2 `ToolCallChunk`

Auto-expands to `ToolCallStart → ToolCallArgs → ToolCallEnd`.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ToolCallChunk"` | **Yes** | |
| `toolCallId` | string | **Yes** | Tool call ID |
| `toolCallName` | string | **On first chunk** | Required on the first chunk for this `toolCallId` |
| `parentMessageId` | string | No | |
| `delta` | string | No | Argument content |

**Auto-close behavior:** The consumer auto-emits `ToolCallEnd` when:
- A new `toolCallId` appears on a subsequent `ToolCallChunk`
- The stream ends

### 13.3 `ReasoningMessageChunk`

Auto-expands to `ReasoningMessageStart → ReasoningMessageContent → ReasoningMessageEnd`.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | `"ReasoningMessageChunk"` | **Yes** | |
| `messageId` | string | **Yes (on first event)** | Must be non-empty on the first chunk |
| `delta` | string | No | Thought content |

**Auto-close behavior:** The consumer auto-emits `ReasoningMessageEnd` when:
- `delta` is empty
- A non-reasoning event appears next

**Note:** Chunk events are consumed and expanded by compliant consumers. Producers using chunk
events do NOT need to also emit the corresponding Start/End events — the consumer handles that.
Producers should choose one approach (explicit lifecycle OR chunk events) and not mix them for
the same entity.

---

## 14. Concurrency Rules

### 14.1 Multiple open text messages: ALLOWED

The protocol explicitly permits multiple `TextMessageStart` events with different `messageId`s
to be in-flight simultaneously. `TextMessageContent` and `TextMessageEnd` events are matched by
`messageId`, so interleaving is unambiguous.

```
TextMessageStart(messageId=A, role=assistant)
TextMessageStart(messageId=B, role=assistant)
TextMessageContent(messageId=A, delta="Hello from A")
TextMessageContent(messageId=B, delta="Hello from B")
TextMessageEnd(messageId=A)
TextMessageEnd(messageId=B)
```

This is the mechanism for emitting concurrent subagent output in parallel orchestration.

### 14.2 Multiple open tool calls: ALLOWED

Multiple `ToolCallStart` events with different `toolCallId`s may be in-flight simultaneously.

```
ToolCallStart(toolCallId=tc1, toolCallName=search)
ToolCallStart(toolCallId=tc2, toolCallName=fetch)
ToolCallArgs(toolCallId=tc1, delta=...)
ToolCallArgs(toolCallId=tc2, delta=...)
ToolCallEnd(toolCallId=tc1)
ToolCallEnd(toolCallId=tc2)
ToolCallResult(toolCallId=tc1, ...)
ToolCallResult(toolCallId=tc2, ...)
```

### 14.3 Reasoning during tool calls: ALLOWED

Reasoning events may be interleaved with tool call events. Both can be in-flight at the same
time. The `ReasoningEncryptedValue` event explicitly supports attaching reasoning to tool calls.

### 14.4 Nested runs via `parentRunId`

A subagent run is expressed by emitting a `RunStarted` with `parentRunId` set to the parent
run's `runId`. The subagent's events appear inline in the same stream, nested by `parentRunId`
reference. The parent run's `RunFinished` must come after all child runs have finished.

```
RunStarted(runId=parent, threadId=session)
  RunStarted(runId=child-A, parentRunId=parent, threadId=session)
  ... child-A events ...
  RunFinished(runId=child-A)
  RunStarted(runId=child-B, parentRunId=parent, threadId=session)
  ... child-B events ...
  RunFinished(runId=child-B)
RunFinished(runId=parent)
```

Consumers reconstruct the run tree by joining on `parentRunId`.

### 14.5 Step nesting: NOT defined

The protocol has no concept of nested steps. Steps are flat within a run. For subagent
hierarchies, use nested runs (§14.4) rather than nested steps.

### 14.6 What CANNOT be concurrent

- Two `ReasoningStart` events for the same outer `messageId` without an intervening `ReasoningEnd`.
- Two `TextMessageStart` events with the **same** `messageId`.
- Two `ToolCallStart` events with the **same** `toolCallId`.
- `StepStarted` for a `stepName` that is already open (same `stepName` may not be re-opened
  until its `StepFinished` has been emitted).

---

## 15. Complete Conformance Rules

A stream is conformant if and only if all of the following hold. These rules can be applied
mechanically to validate any event stream.

### R1 — Run boundary
- The stream contains at least one run.
- Each run begins with `RunStarted` and ends with exactly one of `RunFinished` or `RunError`.
- No events belonging to a run appear before its `RunStarted` or after its `RunFinished`/`RunError`.

### R2 — Run ID consistency
- `RunFinished.runId` and `RunFinished.threadId` must match the corresponding `RunStarted`.
- All events within a run share the same `threadId`.

### R3 — Step pairing
- Every `StepStarted(stepName=X)` must have a corresponding `StepFinished(stepName=X)` later
  in the same run.
- `StepFinished` may not appear without a preceding matching `StepStarted`.
- No `StepStarted(stepName=X)` may appear while `stepName=X` is already open.

### R4 — Text message lifecycle
- Every `TextMessageStart(messageId=M)` must have a corresponding `TextMessageEnd(messageId=M)`
  before `StepFinished` (if inside a step) and before `RunFinished`/`RunError`.
- `TextMessageContent(messageId=M)` may only appear after `TextMessageStart(messageId=M)` and
  before `TextMessageEnd(messageId=M)`.
- `TextMessageContent.delta` must be non-empty.
- No `TextMessageStart(messageId=M)` may appear while `messageId=M` is already open.

### R5 — Tool call lifecycle
- Every `ToolCallStart(toolCallId=T)` must have a corresponding `ToolCallEnd(toolCallId=T)`.
- `ToolCallArgs(toolCallId=T)` may only appear after `ToolCallStart(toolCallId=T)` and before
  `ToolCallEnd(toolCallId=T)`.
- `ToolCallResult.toolCallId` must reference a `toolCallId` from a preceding `ToolCallStart`.
- No `ToolCallStart(toolCallId=T)` may appear while `toolCallId=T` is already open.

### R6 — Reasoning lifecycle
- Every `ReasoningStart(messageId=R)` must have a corresponding `ReasoningEnd(messageId=R)`.
- Every `ReasoningMessageStart(messageId=M)` must have a corresponding `ReasoningMessageEnd(messageId=M)`.
- `ReasoningMessageContent.delta` must be non-empty.
- `ReasoningMessageStart`/`Content`/`End` events must appear between `ReasoningStart` and
  `ReasoningEnd` with the matching outer `messageId`.
- `ReasoningMessageEnd` must come before the next `ReasoningMessageStart` for the same outer
  reasoning group.
- The outer `ReasoningStart.messageId` and any inner `ReasoningMessageStart.messageId` must
  be distinct values.
- After `ReasoningEnd(messageId=R)`, no further reasoning events with outer `messageId=R`.

### R7 — Closure before step end
- All `TextMessageStart` events opened within a step must be closed with `TextMessageEnd`
  before `StepFinished`.
- All `ToolCallStart` events opened within a step must be closed with `ToolCallEnd` before
  `StepFinished`.
- All `ReasoningStart` events opened within a step must be closed with `ReasoningEnd` before
  `StepFinished`.

### R8 — Closure before run end
- All open steps must be closed with `StepFinished` before `RunFinished`/`RunError`.
- All open text messages, tool calls, and reasoning blocks that are not within a step must
  be closed before `RunFinished`/`RunError`.

### R9 — Delta non-empty
- `TextMessageContent.delta` must not be empty or whitespace-only.
- `ReasoningMessageContent.delta` must not be empty.

### R10 — ID uniqueness
- `messageId` values must be unique within the run for text messages.
- `toolCallId` values must be unique within the run.
- Reasoning outer `messageId` values must be unique within the run.
- Reasoning inner `messageId` values must be unique within the reasoning group.

### R11 — ActivityDelta requires prior ActivitySnapshot
- `ActivityDelta(messageId=M)` may only appear after `ActivitySnapshot(messageId=M)`.

### R12 — Child runs close before parent
- If a run has child runs (via `parentRunId`), all child `RunFinished`/`RunError` events
  must appear before the parent `RunFinished`/`RunError`.

---

## 16. Valid Stream Examples

### 16.1 Minimal conformant run

```
RunStarted(runId=r1, threadId=t1)
RunFinished(runId=r1, threadId=t1)
```
Valid. A run with no events is conformant.

### 16.2 Standard single-step assistant response

```
RunStarted(runId=r1, threadId=t1)
StepStarted(stepName=step-1)
TextMessageStart(messageId=msg-1, role=assistant)
TextMessageContent(messageId=msg-1, delta="Hello, ")
TextMessageContent(messageId=msg-1, delta="how can I help?")
TextMessageEnd(messageId=msg-1)
StepFinished(stepName=step-1)
RunFinished(runId=r1, threadId=t1)
```

### 16.3 Response with reasoning

```
RunStarted(runId=r1, threadId=t1)
StepStarted(stepName=step-1)
ReasoningStart(messageId=reasoning-1)
ReasoningMessageStart(messageId=block-1, role=assistant)
ReasoningMessageContent(messageId=block-1, delta="Let me think about this...")
ReasoningMessageEnd(messageId=block-1)
ReasoningEnd(messageId=reasoning-1)
TextMessageStart(messageId=msg-1, role=assistant)
TextMessageContent(messageId=msg-1, delta="Here is my answer.")
TextMessageEnd(messageId=msg-1)
StepFinished(stepName=step-1)
RunFinished(runId=r1, threadId=t1, result="Here is my answer.")
```

### 16.4 Tool call within a step

```
RunStarted(runId=r1, threadId=t1)
StepStarted(stepName=step-1)
ToolCallStart(toolCallId=tc1, toolCallName=search, parentMessageId=step-1)
ToolCallArgs(toolCallId=tc1, delta='{"query":"AAPL"}')
ToolCallEnd(toolCallId=tc1)
ToolCallResult(toolCallId=tc1, messageId=step-1, content='{"price":185.92}')
TextMessageStart(messageId=msg-1, role=assistant)
TextMessageContent(messageId=msg-1, delta="AAPL is trading at $185.92.")
TextMessageEnd(messageId=msg-1)
StepFinished(stepName=step-1)
RunFinished(runId=r1, threadId=t1)
```

### 16.5 Parallel subagent runs

```
RunStarted(runId=orchestrator, threadId=t1)
StepStarted(stepName=orchestrator-step)
  RunStarted(runId=branch-A, parentRunId=orchestrator, threadId=t1)
  StepStarted(stepName=branch-A-step)
  TextMessageStart(messageId=msg-A, role=assistant)
  TextMessageContent(messageId=msg-A, delta="Paris")
  TextMessageEnd(messageId=msg-A)
  StepFinished(stepName=branch-A-step)
  RunFinished(runId=branch-A, threadId=t1)

  RunStarted(runId=branch-B, parentRunId=orchestrator, threadId=t1)
  StepStarted(stepName=branch-B-step)
  TextMessageStart(messageId=msg-B, role=assistant)
  TextMessageContent(messageId=msg-B, delta="Paris")
  TextMessageEnd(messageId=msg-B)
  StepFinished(stepName=branch-B-step)
  RunFinished(runId=branch-B, threadId=t1)

  Custom(name=aggregation, value={policy=MAJORITY_VOTE, winner=branch-A, agreement=2})
  TextMessageStart(messageId=msg-final, role=assistant)
  TextMessageContent(messageId=msg-final, delta="The capital of France is Paris.")
  TextMessageEnd(messageId=msg-final)
StepFinished(stepName=orchestrator-step)
RunFinished(runId=orchestrator, threadId=t1)
```

### 16.6 Interrupted run (HITL)

```
RunStarted(runId=r1, threadId=t1)
StepStarted(stepName=step-1)
TextMessageStart(messageId=msg-1, role=assistant)
TextMessageContent(messageId=msg-1, delta="Should I proceed with deletion?")
TextMessageEnd(messageId=msg-1)
StepFinished(stepName=step-1)
RunFinished(runId=r1, threadId=t1, outcome=interrupt,
  interrupt={prompt="Confirm deletion", options=["Yes","No"]})
```

On resume, a new run starts:
```
RunStarted(runId=r2, threadId=t1, parentRunId=r1)
... (continued execution) ...
RunFinished(runId=r2, threadId=t1)
```

---

## 17. Invalid Stream Examples

### 17.1 Missing RunFinished

```
RunStarted(runId=r1, threadId=t1)
TextMessageStart(messageId=msg-1, role=assistant)
TextMessageContent(messageId=msg-1, delta="Hello")
TextMessageEnd(messageId=msg-1)
← INVALID: no RunFinished or RunError
```

**Violation:** R1 — run not closed.

### 17.2 StepFinished without matching StepStarted

```
RunStarted(runId=r1, threadId=t1)
StepFinished(stepName=step-1)   ← INVALID
RunFinished(runId=r1, threadId=t1)
```

**Violation:** R3 — `StepFinished` without preceding `StepStarted`.

### 17.3 Text message not closed before StepFinished

```
RunStarted(runId=r1, threadId=t1)
StepStarted(stepName=step-1)
TextMessageStart(messageId=msg-1, role=assistant)
TextMessageContent(messageId=msg-1, delta="Hello")
StepFinished(stepName=step-1)   ← INVALID: msg-1 still open
RunFinished(runId=r1, threadId=t1)
```

**Violation:** R7 — open text message not closed before `StepFinished`.

### 17.4 Empty delta

```
TextMessageContent(messageId=msg-1, delta="")   ← INVALID
```

**Violation:** R9 — `delta` must be non-empty.

### 17.5 ReasoningEnd before ReasoningMessageEnd

```
ReasoningStart(messageId=reasoning-1)
ReasoningMessageStart(messageId=block-1, role=assistant)
ReasoningMessageContent(messageId=block-1, delta="thinking...")
ReasoningEnd(messageId=reasoning-1)   ← INVALID: block-1 still open
ReasoningMessageEnd(messageId=block-1)
```

**Violation:** R6 — `ReasoningEnd` while inner block is still open; R7 — `ReasoningMessageEnd`
appears after the reasoning group is closed.

### 17.6 ActivityDelta without prior ActivitySnapshot

```
ActivityDelta(messageId=msg-1, activityType=PLAN, patch=[...])   ← INVALID
```

**Violation:** R11 — no preceding `ActivitySnapshot` for `messageId=msg-1`.

### 17.7 Child run finishes after parent

```
RunStarted(runId=parent, threadId=t1)
  RunStarted(runId=child, parentRunId=parent, threadId=t1)
RunFinished(runId=parent, threadId=t1)   ← INVALID: child not yet finished
RunFinished(runId=child, threadId=t1)
```

**Violation:** R12 — parent closed while child still open.

### 17.8 Duplicate open step

```
StepStarted(stepName=step-1)
StepStarted(stepName=step-1)   ← INVALID: step-1 already open
```

**Violation:** R3 — same `stepName` opened while already open.

---

## 18. Producer Checklist

Before emitting events, verify:

**Run structure**
- [ ] First event is `RunStarted` with unique `runId` and correct `threadId`
- [ ] Last event is `RunFinished` or `RunError`
- [ ] `RunFinished.runId` matches `RunStarted.runId`
- [ ] Subagent runs have `parentRunId` set to the orchestrator's `runId`

**Step structure**
- [ ] Every `StepStarted` has a matching `StepFinished` with the same `stepName`
- [ ] All text messages, tool calls, and reasoning blocks opened within a step are closed before `StepFinished`

**Text messages**
- [ ] Every `TextMessageStart` has a `role` field
- [ ] Every `TextMessageStart` has a matching `TextMessageEnd`
- [ ] All `TextMessageContent.delta` values are non-empty
- [ ] A final `TextMessageContent` with the full accumulated text is emitted before `TextMessageEnd` (for non-streaming or streaming close)

**Tool calls**
- [ ] Every `ToolCallStart` has a `toolCallName` and `toolCallId`
- [ ] `ToolCallStart.parentMessageId` is set to the current step name
- [ ] Every `ToolCallStart` has a matching `ToolCallEnd`
- [ ] `ToolCallResult.messageId` is set (links result to originating step)

**Reasoning**
- [ ] Every `ReasoningStart` has a matching `ReasoningEnd` with the same `messageId`
- [ ] Every `ReasoningMessageStart` has a matching `ReasoningMessageEnd` with the same (inner) `messageId`
- [ ] `ReasoningMessageStart.role` is always `"assistant"`
- [ ] All `ReasoningMessageContent.delta` values are non-empty
- [ ] `ReasoningEnd` emitted before `StepFinished`

**Custom events**
- [ ] `Custom` events have both `name` and `value` fields
- [ ] `value` is a structured object, not a flat string

---

## 19. Consumer Requirements

A conformant consumer must:

1. **Process events in arrival order.** State (text buffers, tool arg buffers, reasoning buffers)
   must be updated sequentially.

2. **Be resilient to out-of-order delivery.** The protocol states producers should be resilient
   to this. Consumers may need to buffer and reorder by timestamp or sequence number if the
   transport does not guarantee order.

3. **Match events by ID, not by position.** Use `messageId`, `toolCallId`, and `stepName` to
   associate related events — do not assume positional proximity.

4. **Request `StateSnapshot` on desync.** If a `StateDelta` cannot be applied (patch target
   missing), request a fresh `StateSnapshot` rather than silently dropping the delta.

5. **Request `ActivitySnapshot` on desync.** Same recovery pattern for `ActivityDelta`.

6. **Expand chunk events correctly.** `TextMessageChunk`, `ToolCallChunk`, and
   `ReasoningMessageChunk` must be expanded to their Start/Content/End equivalents. The first
   chunk for a new ID triggers the Start; subsequent chunks for the same ID are Content;
   auto-close on ID change or stream end triggers End.

7. **Handle `parentRunId` to reconstruct run trees.** Group runs by `parentRunId` to build the
   orchestration tree. Render parent run events and child run events in their respective tree nodes.

8. **Treat `RunFinished(outcome=interrupt)` as a pause, not a terminal end.** The run may be
   resumed by a subsequent `RunStarted` with `parentRunId` pointing to this run's `runId`.

9. **Not rely on `rawEvent` for required fields.** `rawEvent` is a convenience field for
   debugging; required fields must be present at the typed level.

---

## 20. Deprecated Events

These events exist in the wild but are replaced. Consumers should handle them for backward
compat but producers must not emit them.

| Deprecated Type | Replacement | Notes |
|----------------|-------------|-------|
| `THINKING_START` | `ReasoningStart` | Same semantics, new name |
| `THINKING_END` | `ReasoningEnd` | Same semantics, new name |
| `THINKING_TEXT_MESSAGE_START` | `ReasoningMessageStart` | Same semantics, new name |
| `THINKING_TEXT_MESSAGE_CONTENT` | `ReasoningMessageContent` | Same semantics, new name |
| `THINKING_TEXT_MESSAGE_END` | `ReasoningMessageEnd` | Same semantics, new name |

Our codebase has fully migrated to the `Reasoning*` naming. No deprecated event names remain
in server or client code.

---

## 21. Implementation Notes and Quirks

### 21.1 `compactEvents()` normalization

The spec mentions a `compactEvents()` utility that normalizes event streams — specifically,
the `input` field in `RunStarted` "may omit messages already present in history; compactEvents()
will normalize." This implies a deduplication/compaction step exists in the reference
implementation for reconstructing complete histories from partial streams.

### 21.2 `TextMessageContent` as sync event before `TextMessageEnd`

A common pattern (and what our producer does) is to emit a `TextMessageContent` with the full
accumulated text immediately before `TextMessageEnd`. This serves as a sync signal — consumers
that missed earlier streaming chunks can recover the full text from this single event. It is
conformant: `TextMessageContent` before `TextMessageEnd` for the same `messageId` is valid.

### 21.3 `ToolCallResult` timing relative to `ToolCallEnd`

These are structurally independent. `ToolCallEnd` closes the argument stream immediately when
the agent stops generating args. `ToolCallResult` arrives later, when the tool execution
completes. A consumer must not expect them to be adjacent in the stream.

### 21.4 `ReasoningEncryptedValue` is client-opaque

The client must store and forward this blob without inspection or modification. Only the agent
runtime can decrypt it. It is not conversation content — it is agent-private state that happens
to be stored client-side for the zero data retention use case.

### 21.5 `StateDelta` is RFC 6902, not a merge patch

RFC 7386 (JSON Merge Patch) and RFC 6902 (JSON Patch) are different. `StateDelta` and
`ActivityDelta` use RFC 6902. Consumers must implement the full JSON Patch operation set
(`add`, `remove`, `replace`, `move`, `copy`, `test`), not a simple deep merge.

### 21.6 `role` defaults in chunk events

`TextMessageChunk.role` defaults to `"assistant"` when absent. Producers should always set it
explicitly on the first chunk to avoid ambiguity.

### 21.7 `Custom` event `value` field

The spec requires `value` to be present. Our `CorrectionEvent` and `Reasoning*Event` classes
bypass this: correction spreads fields at top level; reasoning events override `type` entirely
to emit non-CUSTOM event types. Both work in practice because the client reads from `rawEvent`,
but strict consumers expecting `value` will not find it on correction events.

### 21.8 Reasoning outer vs. inner `messageId` are distinct

A common mistake: using the same `messageId` for both `ReasoningStart` and `ReasoningMessageStart`.
They must be different. The outer ID identifies the reasoning group across its entire lifetime;
the inner ID identifies a single thought block within that group. Reusing the same value violates
R10 and breaks consumers that track both IDs.

### 21.9 Event mapper instances are per-agent-stream

`AGUIEventMapper` is stateful — it tracks open message IDs, reasoning IDs, step names, and tool
call state. In orchestration, each agent's event stream must be processed by its own mapper
instance. Sharing a mapper across agents corrupts all of this state.
