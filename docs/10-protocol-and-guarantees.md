# 10. Protocol and Guarantees (Detailed)

This document defines the runtime protocol enforced by the current codebase.
It is normative for behavior implemented in:

- `engine` request/response processors
- `AgentRunLifecyclePlugin`, `HumanInTheLoopPlugin`, `ModelInvocationPipeline`, `ContextManagementPlugin`, and `GuardrailPlugin`
- `ParallelOrchestratorAgent`
- REST event mapper `AGUIEventMapper`

## 10.1 Terms

- **Run**: One ADK invocation (`invocationId`), may contain multiple model turns.
- **Turn**: A model response cycle with optional tools and completion signal.
- **Partial response**: A streaming chunk with `partial=true`.
- **Finalized response**: A non-partial response (`partial=false`).
- **Violation**: Structured runtime correction signal (`Violation`) stored in run state.

## 10.2 Deterministic Processing Order

The model invocation plugins apply the following order.

### Before model

1. `HumanInTheLoopPlugin` short-circuit checks
2. `HumanInTheLoopRequestProcessor` resume mapping
3. `CorrectionProcessor`
4. `PlanningRequestProcessor`
5. context manager prompt rebuild (if configured)

### After model

1. `ToolCallSanitizationResponseProcessor`
2. `PlanLoopResponseProcessor`
3. `TurnCompletionResponseProcessor`
4. `PartOrderingResponseProcessor`
5. finish-reason normalization (`STOP` if run is complete and no tools remain)

This order is part of runtime semantics. Reordering changes behavior.

## 10.3 Request Protocol Guarantees

## 10.3.1 Human-in-the-loop pause/resume

If session is paused (`SessionStateUtils.isPaused`):

- For `TOOL_CONFIRMATION` (or pending `adk_request_confirmation` call ID exists):
  - missing user answer: invocation ends immediately with a model event asking for confirmation
  - missing pending confirmation ID: invocation ends immediately with a model event explaining no pending confirmation exists
  - valid answer: pause is cleared, latest user content is replaced with a function-response payload for `adk_request_confirmation`

- For non-tool-confirmation pause:
  - missing answer: invocation ends immediately with a clarification-required model event
  - valid answer: pause is cleared and a synthesized resume content is appended to request contents

Pause reasons currently recognized:

- `user_clarification`
- `tool_confirmation`
- `guardrail_escalation`

## 10.3.2 Violation correction injection

`CorrectionProcessor` converts pending run-state violations into corrective events/content and appends them to the next request payload.

Guarantees:

- all violations with non-blank `correctionMessage` are emitted
- violations are cleared after emission
- emitted events include correction metadata, consumable by mapper/client

## 10.3.3 Planning context injection

If a run-state plan exists, `PlanningRequestProcessor` appends:

- plan summary content
- active-task structural anchor when an open task exists

If no plan exists, this processor is a no-op.

## 10.4 Response Protocol Guarantees

## 10.4.1 Tool sanitization

### Partial responses

If a partial response contains function call or function response parts:

- all tool parts are stripped from that chunk
- violation `partial_tool_calls` is recorded
- correction message explicitly instructs to emit tool payloads only in non-partial turns

### Non-partial responses

If tool calls are exact repeats of prior-turn summarized calls:

- redundant calls are stripped
- violation `redundant_tool_calls` is recorded
- `turnComplete` is forced to `false`

## 10.4.2 Plan-loop enforcement

For finalized responses, when model appears to finish by text (text present, no tool calls):

- if plan validation fails (`PlanningValidator.canSubmitFinalAnswerOrError`):
  - violation `final_answer_validation` is recorded
  - non-thought text parts are converted into `thought=true`
  - `turnComplete` is forced to `false`

For non-finishing non-tool turns with active open task:

- violation `incomplete_task` is recorded
- `turnComplete` is forced to `false`

## 10.4.3 Turn completion synthesis

For non-partial responses without explicit `turnComplete`:

`turnComplete` is set true if any condition holds:

- tool parts exist
- non-thought text exists
- model has finish reason

Otherwise false.

## 10.4.4 Canonical part ordering

Final response parts are sorted into fixed order:

1. thought parts
2. regular text parts
3. function-call parts
4. function-response parts
5. anything else

This provides stable downstream event sequencing.

## 10.4.5 Finish reason normalization

After response processors, `ModelInvocationPipeline` sets finish reason to `STOP` when:

- `turnComplete=true`
- no tool parts remain

## 10.5 Guardrail Enforcement Protocol

`GuardrailPlugin` enforces guardrails by stage.

## 10.5.1 Input stage (`beforeModelCallback`)

- evaluates configured INPUT guardrails on latest user text
- `ALLOW`: continue
- `WARN`: continue and record violation
- `BLOCK`: return guardrail response immediately
- `ESCALATE`: pause session and return guardrail response

## 10.5.2 Tool stage (`beforeToolCallback`)

- evaluates TOOL guardrails with tool descriptor + args
- `ALLOW`: continue
- `WARN`: continue and record violation
- `BLOCK`: return blocked tool result payload
- `ESCALATE`:
  - requests native confirmation through tool context
  - pauses session with reason `tool_confirmation`
  - returns confirmation-requested payload

## 10.5.3 Output stage (`afterModelCallback` + `onEventCallback`)

### SYNC mode

- guardrails evaluated immediately on finalized output text
- `ALLOW`: pass through
- `WARN`: record violation; may request regeneration when `retry_required=true` or relevance steer code
- `BLOCK`/`ESCALATE`: replace output text with block/escalation message; escalate also pauses session

### OPTIMISTIC mode

- output decision is scheduled asynchronously
- final/terminal events wait briefly for decision (`200ms`)
- if blocking/escalating decision arrives:
  - violation recorded
  - invocation ended via event actions
  - event content replaced with block message

## 10.5.4 Guardrail fallback mode on internal errors

From guardrail policy config:

- `FAIL_OPEN`: allow execution on guardrail runtime failures
- `FAIL_CLOSED`: block on guardrail runtime failures

## 10.5.5 Guardrail code catalog

Core codes (from `GuardrailConstants`):

- `guardrail_allow`
- `guardrail_violation`
- `guardrail_runtime_warn`
- `guardrail_runtime_block`
- `guardrail_input_length`
- `guardrail_input_pattern`
- `guardrail_output_length`
- `guardrail_output_pattern`
- `guardrail_output_block`
- `guardrail_tool_policy`
- `guardrail_tool_escalate`
- `guardrail_tool_block`
- `relevance_steer`
- `relevance_block`

## 10.6 Parallel Orchestration Protocol

`ParallelOrchestratorAgent` rules:

- success is strict: branch is successful only when terminal event has `turnComplete=true`
- only aggregated orchestrator output is emitted (single final event)
- stopping policies:
  - `ALL_COMPLETE`
  - `FIRST_SUCCESS`
  - `QUORUM`
- aggregation policies:
  - `CONCATENATE`
  - `BEST_EFFORT`
  - `MAJORITY_VOTE`

When required success target is not met:

- deterministic best-effort fallback is used
- violation `parallel_policy_fallback` is recorded with details:
  - stopping policy
  - aggregation policy
  - required successes
  - successful count
  - completed count

## 10.7 Event Mapping Protocol (REST / AG-UI)

`AGUIEventMapper` guarantees:

- emits `RunStartedEvent` once at first mapped runtime event
- starts step automatically when no step exists
- step finishes only when incoming runtime event has `turnComplete=true`
- `onComplete()` always emits `RunFinishedEvent` (and closes pending step)
- `onError()` emits `RunErrorEvent`

Thinking/message/tool ordering behavior:

- thinking stream is opened with `ThinkingStartEvent` and message start event
- thinking is explicitly closed before emitting text/tool events in same chunk
- partial text emits chunk events; finalized text emits content and end events
- function calls map to start/args/end tool-call events
- function responses map to tool-call-result event

Metadata guarantee:

- each emitted AG-UI event is decorated with timestamp and raw event map containing at least `agentId` and `threadId`.

## 10.8 Runtime Violation Code Catalog (Non-guardrail)

Codes emitted directly by runtime processors/orchestrators:

- `partial_tool_calls`
- `redundant_tool_calls`
- `incomplete_task`
- `final_answer_validation`
- `parallel_policy_fallback`

All violations are stored in run state and consumed by `CorrectionProcessor` on the next request cycle.

## 10.9 Conformance Notes

To preserve protocol guarantees, changes should maintain:

- processor ordering in `ModelInvocationPipeline`
- explicit handling of partial vs finalized responses
- deterministic part ordering
- deterministic fallback behavior in parallel orchestration
- explicit violation emission for correctable protocol failures
