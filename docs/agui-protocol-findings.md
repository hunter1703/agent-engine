# AGUI Protocol — Findings, Gaps, and Quirks

This document records what we learned from auditing the full AGUI protocol specification
(`agui.html`) against our implementation. It is intended as a living reference for anyone
working on `AGUIEventMapper`, the REST event layer, or client-side adapters.

---

## 1. Protocol-Mandated Fields We Were Missing (Now Fixed)

### 1.1 Reasoning events require `messageId`

The spec mandates `messageId` on every `REASONING_*` event:
- `ReasoningStart` → `messageId` (required)
- `ReasoningMessageStart` → `messageId`, `role` (required)
- `ReasoningMessageContent` → `messageId`, `delta` (required)
- `ReasoningMessageEnd` → `messageId` (required)
- `ReasoningEnd` → `messageId` (required)

Our previous `ThinkingStartEvent` / `ThinkingEndEvent` (using the deprecated THINKING_* names)
carried no `messageId`. Fixed by introducing the five `Reasoning*Event` custom classes.

### 1.2 `TextMessageStart` requires `role`

Spec: `role` is required on `TextMessageStart`. We were not setting it. Now set to `"assistant"`
in `startTextMessageIfNeeded()`.

### 1.3 `ToolCallStart.parentMessageId` and `ToolCallResult.messageId`

Both are optional per spec but semantically required for tool call traceability — the UI needs
to know which step/message triggered a tool call and where to attach the result. Fixed: mapper
now populates `parentMessageId` from `state.currentStepName` and `messageId` on result from
the tracked `toolCallParentSteps` map.

### 1.4 `CUSTOM` event spec: `name` + `value`

The protocol defines `Custom` events as requiring `name` and `value` fields. Our `CorrectionEvent`
extends `BaseCustomEvent` (which carries `name`) but exposes individual fields (`correctionType`,
`code`, `message`) rather than a single `value` object. The client adapter reads these individual
fields from `rawEvent` and it works in practice, but it deviates from the protocol shape.

**Quirk to be aware of**: If a strict AGUI-compliant consumer processes our `CUSTOM` correction
events, it will look for a `value` field and find none. Future work: wrap correction payload into
a `value` object and keep individual fields for backward compat.

---

## 2. Protocol Rules for Valid Event Sequences

### 2.1 Run lifecycle (hard constraint)

```
RunStarted → ... → (RunFinished | RunError)
```

- `RunStarted` is mandatory and must be the first event of a run.
- Exactly one of `RunFinished` or `RunError` must close the run.
- No events belonging to this run may appear after the closing event.
- Our `AGUIEventMapper` emits `RunStartedEvent` on new `invocationId` and `RunFinishedEvent`
  on `finishReason` present. ✓

### 2.2 Step pairing (hard constraint)

```
StepStarted(stepName=X) → ... → StepFinished(stepName=X)
```

- `stepName` in `StepFinished` must exactly match the corresponding `StepStarted`.
- Our mapper tracks `state.currentStepName` and passes it through. ✓
- **Critical bug we fixed**: The correction event path had an early return that skipped
  `finishStepIfNeeded()`, violating this invariant. Fixed by restructuring to `if/else`.

### 2.3 Text message sequence

```
TextMessageStart(messageId=M) → TextMessageContent(messageId=M)* → TextMessageEnd(messageId=M)
```

- `delta` in `TextMessageContent` must be non-empty.
- `TextMessageChunk` is a convenience alias that auto-expands to the above. The spec handles
  auto-close on: new `messageId` appearing, or stream completion.
- Our mapper emits `TextMessageChunkEvent` (streaming) and then `TextMessageContentEvent` +
  `TextMessageEndEvent` at close. This is valid — `TextMessageContent` (full sync) before
  `TextMessageEnd` is the correct pattern for non-streaming or closing a streaming message.

### 2.4 Tool call sequence

```
ToolCallStart(toolCallId=T) → ToolCallArgs(toolCallId=T)* → ToolCallEnd(toolCallId=T)
ToolCallResult(toolCallId=T, messageId=M)   [separate, after tool execution]
```

- `ToolCallResult` is structurally separate from `ToolCallEnd`. `End` closes the call argument
  stream; `Result` delivers the execution output. Our mapper correctly separates these.
- `delta` in `ToolCallArgs` is typically a JSON fragment.

### 2.5 Reasoning sequence

```
ReasoningStart(messageId=R)
  ReasoningMessageStart(messageId=M1, role="assistant")
  ReasoningMessageContent(messageId=M1, delta=...)*
  ReasoningMessageEnd(messageId=M1)
  [additional ReasoningMessage* blocks...]
ReasoningEnd(messageId=R)
```

- The outer `messageId` (on `ReasoningStart`/`ReasoningEnd`) identifies the reasoning group.
- The inner `messageId` (on `ReasoningMessage*`) identifies a single thought block within the group.
- These are different IDs. Our mapper correctly uses `currentReasoningId` for the outer and
  `currentReasoningMessageId` for the inner.
- `ReasoningEnd` must close `ReasoningStart`. `ReasoningMessageEnd` closes `ReasoningMessageStart`.
- **No further events with the same `messageId` after `ReasoningEnd`** — our state nulls both IDs
  in `endReasoningIfNeeded()`. ✓

---

## 3. Concurrency Rules (What the Protocol Allows)

### 3.1 Multiple open text messages: ALLOWED

The protocol explicitly allows multiple `TextMessageStart` events with different `messageId`s to
be open simultaneously. Content events target a specific `messageId`. This is relevant for
parallel orchestration — branch agents can emit text messages concurrently with distinct IDs.

**Current limitation**: Our `AGUIEventMapper` assumes one open text message at a time
(`state.currentTextMessageId`). For single-agent use this is correct. For parallel mode,
each branch needs its own mapper instance.

### 3.2 Multiple open tool calls: ALLOWED

Multiple `ToolCallStart` events with different `toolCallId`s can be in-flight simultaneously.
Our mapper allows this via `toolCallParentSteps` map (multiple entries). ✓

### 3.3 Reasoning during tool calls: ALLOWED

The spec does not prohibit reasoning events interleaved with tool call events. Our ordering
heuristic (close reasoning before emitting tool calls) is conservative but correct — it avoids
ambiguity at the cost of prematurely closing a reasoning block when a tool call arrives. This
is acceptable: models that reason before calling a tool produce thought then action, not
thought-while-acting.

### 3.4 Step nesting: NOT defined

The spec has no concept of nested steps. `StepStarted`/`StepFinished` are flat within a run.
For subagent orchestration (where we want to show subagent work nested under a parent step),
we need either:
- A custom `SubagentStarted`/`SubagentFinished` event pair (recommended), OR
- Use `parentRunId` on a new `RunStarted` for each subagent run (see §4)

---

## 4. Important Protocol Features Not Yet Used

### 4.1 `parentRunId` in `RunStarted`

The spec defines `parentRunId` (optional) in `RunStarted` to express run lineage for
branching/time-travel scenarios. This is precisely the right primitive for subagent orchestration:

```
RunStarted(runId="orchestrator-run-1", threadId="session-1")
  RunStarted(runId="branch-A-run", parentRunId="orchestrator-run-1", threadId="session-1")
  RunFinished(runId="branch-A-run")
  RunStarted(runId="branch-B-run", parentRunId="orchestrator-run-1", threadId="session-1")
  RunFinished(runId="branch-B-run")
RunFinished(runId="orchestrator-run-1")
```

This gives the UI a tree structure it can render. Not yet emitted by our mapper.

### 4.2 `RunFinished.outcome` and `RunFinished.interrupt`

The spec (draft) defines:
- `outcome`: `"success"` | `"interrupt"`
- `interrupt`: structured interrupt details when paused

This maps cleanly to our `HumanInTheLoopTool` pause behavior. Currently we don't set these
fields on `RunFinishedEvent`. A paused run should emit `RunFinished(outcome="interrupt", interrupt={...})`
rather than keeping the stream open — or not emit `RunFinished` at all and let the SSE
connection remain open for resume. This needs a decision.

### 4.3 `ActivitySnapshot` / `ActivityDelta`

The spec defines structured activity events for things like plans, search results, or any
structured side-channel output. These use RFC 6902 JSON Patch for incremental updates.
Currently unused. Planning output (from the planning loop) could be surfaced via
`ActivitySnapshot` rather than as a custom event.

### 4.4 `ReasoningEncryptedValue`

For zero data-retention environments: attach an opaque encrypted blob to a message or tool call
so the agent can reconstruct its reasoning on the next turn without exposing raw thoughts.
Fields: `subtype` ("message" | "tool-call"), `entityId`, `encryptedValue`.
Not currently used or needed, but relevant if we ever support ZTR deployments.

### 4.5 `MetaEvent`

Draft side-band annotation that can appear anywhere in the stream. Could be used for:
- Correction signals (`correctionType`, `code`) — without polluting the main event flow
- Attribution metadata (which agent produced a message)
- Diagnostic annotations

### 4.6 `TextMessageChunk` / `ToolCallChunk` / `ReasoningMessageChunk` convenience events

These auto-expand on the client. We're not emitting them currently — we emit the full
Start/Content/End lifecycle explicitly. Both approaches are valid. The chunk convenience
events are better for streaming from upstream systems that don't want to manage lifecycle.

---

## 5. What We Emit That the Spec Doesn't Define

### 5.1 REASONING_* events as non-CUSTOM top-level types

The ag-ui library's `EventType` enum does not include REASONING_* values. We emit them with
`type: "REASONING_START"` etc. directly, bypassing the CUSTOM wrapper. The spec lists them
as first-class events, so the wire format is correct — but the Java library doesn't know about
them, hence the `@JsonIgnore` / `@JsonProperty("type")` workaround on our custom event classes.

If the ag-ui library is updated to include REASONING_* in `EventType`, our custom classes can
be deleted and replaced with library-provided ones.

### 5.2 `CorrectionEvent` as CUSTOM with individual fields

As described in §1.4 — we emit `type: "CUSTOM"`, `name: "correction"` but with individual
fields instead of a `value` object. Works in practice because the client adapter reads from
`rawEvent` which contains all serialized fields. Deviates from strict spec.

---

## 6. Event Ordering Guarantees Required by Spec

The spec states:
> "Implementations should be resilient to out-of-order delivery."

This means clients must buffer events and reorder by sequence or timestamp if needed. For us
as the producer, we must guarantee:

1. Start events before Content/Delta events (same ID)
2. Content before End (same ID)
3. `StepFinished` after all message/tool/reasoning events it contains
4. `RunFinished` after `StepFinished`

Our `finishStep()` chain (`finalizeTextMessageIfNeeded → closeReasoningIfNeeded → StepFinished`)
and `map()` method chain (`mapEventInternal → runFinishedIfNeeded`) guarantee this ordering
when events are processed synchronously via `concatWith`. ✓

---

## 7. Deprecated Events Reference

| Deprecated | Replacement | Status in our codebase |
|-----------|-------------|----------------------|
| `THINKING_START` | `REASONING_START` | Replaced ✓ |
| `THINKING_END` | `REASONING_END` | Replaced ✓ |
| `THINKING_TEXT_MESSAGE_START` | `REASONING_MESSAGE_START` | Replaced ✓ |
| `THINKING_TEXT_MESSAGE_CONTENT` | `REASONING_MESSAGE_CONTENT` | Replaced ✓ |
| `THINKING_TEXT_MESSAGE_END` | `REASONING_MESSAGE_END` | Replaced ✓ |

All deprecated event names have been purged from server and client code.
