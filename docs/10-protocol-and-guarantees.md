# 10. Protocol and Guarantees (Detailed)

This document defines the runtime protocol enforced by the current codebase.
It is normative for behavior implemented in:

- the agent runtime request/response processors (`agent:infra`)
- `BaseFlow`, `ContextManagementPlugin`, and `GuardrailPlugin`
- `ParallelOrchestratorAgent`
- REST event mapper `AGUIEventMapper`

## 10.1 Terms

- **Run**: One ADK invocation (`invocationId`), may contain multiple model turns.
- **Turn**: A model response cycle with optional tools and ADK-owned terminal semantics.
- **Partial response**: A streaming chunk with `partial=true`.
- **Finalized response**: A non-partial response (`partial=false`).
- **Violation**: Structured runtime correction signal (`Violation`) stored in run state.

## 10.2 Deterministic Processing Order

The model invocation plugins apply the following order.

### Before model

1. ADK `RequestConfirmationLlmRequestProcessor` replay handling
2. `CorrectionProcessor`
3. `ReminderRequestProcessor`
4. context manager prompt rebuild (if configured)
5. guardrail plugin short-circuit, if a guardrail returns a synthetic human-input tool call

### After model

Response processors run in this order (`ToolCallSanitizationResponseProcessor` is prepended at construction so it runs first):

1. `ToolCallSanitizationResponseProcessor` (acts on partial responses only)
2. `ResponseFormatValidationProcessor` (acts on finalized responses only)
3. `PlanLoopResponseProcessor` (acts on finalized responses only)

This order is part of runtime semantics. Reordering changes behavior.

## 10.3 Request Protocol Guarantees

## 10.3.1 Human input pause/resume

Human input is represented only through unresolved `adk_request_confirmation` events.

Guarantees:
- actual tool confirmations replay the original tool call through ADK
- non-tool human input is requested through the internal `request_human_input` tool
- REST resume sends a native `FunctionResponse` for `adk_request_confirmation`
- binary confirmations use `ALLOW` / `DISALLOW` at the API boundary and map to `confirmed=true|false`
- text confirmations use `answer` and map to `confirmed=true` plus `payload.answer`
- no marker text protocol exists

## 10.3.2 Violation correction injection

`CorrectionProcessor` converts pending run-state violations into corrective events/content and appends them to the next request payload.

Guarantees:

- all violations with non-blank `correctionMessage` are emitted
- violations are cleared after emission
- emitted events include correction metadata, consumable by mapper/client

## 10.3.3 Planning context injection

If a run-state plan exists, `ReminderRequestProcessor` appends:

- plan summary content
- active-task structural anchor when an open task exists

If no plan exists, this processor is a no-op.

## 10.4 Response Protocol Guarantees

## 10.4.1 Tool-call sanitization (partial responses)

`ToolCallSanitizationResponseProcessor` acts on partial responses only; finalized responses pass through untouched.

For a partial response that carries tool calls:

- non-tool parts are stripped
- tool calls naming a tool not in the agent's available tool set are removed, and the offending names are collected
- an `invalid_tool_calls` violation is recorded with an `invalidToolNames` detail
- valid tool calls are preserved

## 10.4.2 Response-format validation (finalized responses)

`ResponseFormatValidationProcessor` acts on finalized responses when the agent config declares a `responseFormat` JSON Schema.

- the response text is validated against the schema
- on failure, a `response_format_validation` violation (with a message) is recorded and a continuation is requested so the model can correct its output
- agents without a `responseFormat` are unaffected

## 10.4.3 Plan-loop enforcement

For finalized responses, when model appears to finish by text (text present, no tool calls):

- if plan validation fails (`PlanningValidator.canSubmitFinalAnswerOrError`):
  - violation `final_answer_validation` is recorded
  - non-thought text parts are stripped from the visible response
  - the response is converted into a continuation (`partial=true`) so the model must keep working

The engine does not synthesize `turnComplete` or reorder parts. ADK owns response terminality via
`Event.finalResponse()` and `EventActions.endInvocation()`.

## 10.5 Guardrail Enforcement Protocol

`GuardrailPlugin` enforces guardrails by stage.

## 10.5.1 Input stage (`beforeModelCallback`)

- evaluates configured INPUT guardrails on latest user text
- `ALLOW`: continue
- `WARN`: continue and record violation
- `BLOCK`: return guardrail response immediately
- `ESCALATE`: emit an internal `request_human_input(kind=DECISION)` tool call and let ADK own the pause

## 10.5.2 Tool stage (`beforeToolCallback`)

- evaluates TOOL guardrails with tool descriptor + args
- `ALLOW`: continue
- `WARN`: continue and record violation
- `BLOCK`: return blocked tool result payload
- `ESCALATE`:
  - requests native confirmation through tool context
  - returns confirmation-requested payload

## 10.5.3 Output stage (`afterModelCallback` + `onEventCallback`)

- guardrails evaluated immediately on finalized output text
- `ALLOW`: pass through
- `WARN`: record violation; may request regeneration when `retry_required=true` or relevance steer code
- `BLOCK`/`ESCALATE`: replace output text with block/escalation message; escalate also pauses session

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
- `relevance_steer`
- `relevance_block`

## 10.6 Parallel Orchestration Protocol

`ParallelOrchestratorAgent` rules:

- success is strict: branch is successful only when the terminal event satisfies ADK terminal semantics (`Event.finalResponse()` or `EventActions.endInvocation()`)
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
- step finishes only when incoming runtime event is terminal under ADK semantics (`Event.finalResponse()` or `EventActions.endInvocation()`)
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

- `invalid_tool_calls` (tool-call sanitization)
- `response_format_validation` (response-format validation)
- `final_answer_validation` (plan-loop enforcement)
- `parallel_policy_fallback` (parallel orchestration)

All violations are stored in run state and consumed by `CorrectionProcessor` on the next request cycle.

## 10.9 Conformance Notes

To preserve protocol guarantees, changes should maintain:

- processor ordering in the flow class (`BaseFlow`)
- explicit handling of partial vs finalized responses
- deterministic part ordering
- deterministic fallback behavior in parallel orchestration
- explicit violation emission for correctable protocol failures
