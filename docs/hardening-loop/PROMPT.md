You are running a persistent, self-improving hardening loop for /Users/rhp/Projects/agent-engine.

  This loop runs indefinitely. Its job is not to "finish" but to find issues and make the
  service more correct with every iteration. The quality of test cases should increase over
  time as you accumulate knowledge about the service.

  ════════════════════════════════════════
  ARTIFACTS — read these first, always
  ════════════════════════════════════════

  All persistent state lives in docs/hardening-loop/:
    FEATURE-MATRIX.md   — testable scenarios with status
    ISSUES.md           — bugs found, root cause, status
    FIXES.md            — what was changed and why
    TEST-CASES.md       — reproducible test definitions
    RUN-LOG.md          — per-loop execution record
    KNOWLEDGE.md        — accumulated facts about service behavior (create if absent)
    REPORT.md           — latest summary

  At startup, read ALL of these before doing anything else. Your starting state is
  everything that was learned before. Never repeat a test that is already PASS unless
  you are doing a regression pass after a fix.

  ════════════════════════════════════════
  PHASE 0 — BOOTSTRAP (once per cold start)
  ════════════════════════════════════════

  0a. Read the full artifact set above to determine where the previous run ended.
      If FEATURE-MATRIX.md does not exist, seed it (see below).
      If it exists, continue from where it left off. Do not re-seed.

  0b. Feature matrix seed (only if creating fresh):
      Cover AT MINIMUM these areas; expand based on what you find in the codebase:
      - Session lifecycle (create, restore, missing session, expired)
      - Model CRUD (create, read, update, delete, duplicate, invalid payload, not found)
      - Agent CRUD (create, read, update, delete, duplicate, invalid, not found)
      - Agent invocation (basic, with/without params, invalid agent, invalid session)
      - HITL: pause, resume-accept, resume-reject, resume-wrong-session, double-resume
      - Tool: low-risk happy path, bad args, unknown tool
      - Tool: high-risk confirmation flow end-to-end
      - Event streaming: field presence, ordering, no duplicate IDs, correct terminal event
      - Orchestrator TRANSFER: handoff, state propagation, missing sub-agent
      - Orchestrator SEQUENTIAL: ordering, failure in middle, last step failure
      - Orchestrator PARALLEL: concurrent execution, partial failure
      - Guardrails: each phase (INPUT/TOOL/OUTPUT) with allow/warn/block/escalate behavior
      - Catalog endpoints: list, get, schema validation
      - Error handling: malformed JSON, wrong content-type, missing required fields
      - Concurrency: simultaneous invocations, no state corruption

      Columns: | ID | Area | Scenario | Depth | Priority | Status |
      Depth starts at L1 for all rows. Status starts at UNTESTED.

  0c. Check if server is running: GET http://localhost:18080/health (or equivalent).
      If not running, start via ./deploy/deploy.sh dev.
      Poll health endpoint every 3s for up to 60s.
      If startup fails: diagnose from logs, fix, retry up to 3 times.
      If still failing after 3 attempts: log BLOCKED in RUN-LOG.md and stop.
      Record git SHA in every RUN-LOG entry.

  ════════════════════════════════════════
  PHASE 1 — SYNTHESIZE (every loop, before picking)
  ════════════════════════════════════════

  Read KNOWLEDGE.md and ISSUES.md. Then do the following:

  1. Identify any response fields, status codes, or behaviors observed in prior runs
     that suggest untested scenarios. For example:
     - A response contains a `sessionId` field → test "can I reuse that sessionId?"
     - A 404 was correct but 500 appeared elsewhere → scan for all similar endpoints
     - A tool confirmation was accepted → test what happens if rejected with invalid format
     - A bug class was found in CRUD endpoints → generate same test for all other CRUD ops

  2. Write any new test cases to FEATURE-MATRIX.md (new rows, Status=UNTESTED) or
     upgrade existing rows from L1 to L2/L3/L4 if the lower depth passed.

     Depth ladder:
     - L1: smoke — does the happy path work at all?
     - L2: validation — do error cases return the right HTTP codes and bodies?
     - L3: state/sequencing — do multi-step flows produce correct end state?
     - L4: adversarial — concurrent calls, race conditions, boundary values, injected failures

  3. Prioritize new rows: HIGH if they extend a known bug class or cover an interaction
     not yet tested; MED for new happy paths; LOW for redundant variations.

  This phase ensures the loop generates harder and harder tests over time.

  ════════════════════════════════════════
  PHASE 2 — PICK
  ════════════════════════════════════════

  Select the next row where Status = UNTESTED or FAIL.
  Priority order: FAIL (by severity DESC) → UNTESTED HIGH → UNTESTED MED → UNTESTED LOW.
  If all rows are PASS, go to PHASE 1 and generate deeper tests for the lowest-depth PASS rows.
  If no rows can be deepened (all at L4 PASS), proceed to EXIT CHECK.

  ════════════════════════════════════════
  PHASE 3 — DESIGN
  ════════════════════════════════════════

  Before running any curl, write a hypothesis block:

    Hypothesis [TC-N]:
    Endpoint: METHOD /path
    Setup: [any state that must exist first — create sessions, agents, etc.]
    Variants: [list each curl variant with intent]
    Expected per variant: [exact HTTP status + key response fields + side effects]
    Risk: [what could go wrong that this test would expose]

  This forces deliberate thinking. Bad hypothesis → bad test. Skipping this step is not allowed.

  ════════════════════════════════════════
  PHASE 4 — RUN
  ════════════════════════════════════════

  Execute each curl variant from the hypothesis. Rules:
  - All curl commands must be exact, reproducible, with all headers and bodies explicit.
  - Capture HTTP status, full response body, and relevant headers for every call.
  - For SSE endpoints: capture the full event sequence. Verify: RunStarted appears before
    StepStarted, TextMessageContent before TextMessageEnd, RunFinished is terminal, no
    duplicate event IDs.
  - For concurrency tests: use `curl ... & curl ... & wait` with at least 3 parallel calls,
    repeated 3 times to surface non-determinism.
  - For multi-step flows: perform each step in order; use state from prior responses
    (sessionId, confirmationId, etc.) in subsequent calls.
  - On any unexpected response: capture last 200 lines of server log immediately.

  ════════════════════════════════════════
  PHASE 5 — ANALYZE
  ════════════════════════════════════════

  After getting responses, do the following before recording results:

  1. Compare actual vs expected (from hypothesis). PASS only if ALL variants matched.

  2. For every response body received (pass or fail), inspect ALL fields and ask:
     - Is this field tested anywhere in the feature matrix?
     - Does this field suggest a new state that should be tested?
     - Does this field's value violate any contract (null where non-null expected, wrong type)?

  3. Derive new test cases from observations. Write them to the hypothesis of the next
     relevant TC entry or open new FEATURE-MATRIX rows.
     Minimum: derive at least 2 new test cases per test run.

  4. Check for silent failures: did the response look correct but side effects are wrong?
     (e.g., 200 on update but GET still returns old value, SSE stream closes without RunFinished)

  ════════════════════════════════════════
  PHASE 6 — RECORD
  ════════════════════════════════════════

  RUN-LOG.md — append:

    Loop [N] — [UTC timestamp]
    Git SHA: [sha]
    Scenario: [matrix ID + description + depth]
    Hypothesis: [TC-N]
    Variants run: [list]
    Results: PASS/FAIL per variant with one-line evidence
    New knowledge: [facts added to KNOWLEDGE.md]
    New test cases generated: [matrix IDs]
    Issues opened: [ISSUE-IDs or none]
    Issues fixed this loop: [ISSUE-IDs or none]
    Matrix: [X PASS / Y FAIL / Z UNTESTED of total]

  KNOWLEDGE.md — append any new facts about service behavior:
    Format: [timestamp] [area] — [observation]
    Examples:
      "SSE stream always ends with RunFinished before connection close"
      "POST /v1/agent returns 200 (not 201) on create"
      "Session state persists across server restarts (verified)"
      "Duplicate model ID returns 409 as of fix FIX-002"

  ISSUES.md — on FAIL, open:

    ISSUE-[N]
    Severity: CRITICAL | HIGH | MEDIUM | LOW
    Area: [feature matrix area]
    Scenario: [specific scenario]
    Depth: L1-L4
    Repro: [exact curl sequence, copy-pasteable]
    Expected: [from hypothesis]
    Actual: [what happened, include response body excerpt]
    Server log: [relevant excerpt]
    Root cause: [diagnosis]
    Status: OPEN

  ════════════════════════════════════════
  PHASE 7 — FIX (only if current scenario FAIL)
  ════════════════════════════════════════

  Fix workflow (strict order):
  1. Write a failing automated test first (unit, integration, or curl-based in TEST-CASES.md).
     Record it BEFORE touching production code.
  2. Implement the minimal correct fix. No unrelated refactors.
  3. Restart server if needed. Re-verify health.
  4. Rerun failing test — must now pass.
  5. Rerun all tests in the affected area to check regressions.
     Any regression is a new FAIL row — fix it before continuing.
  6. Record in FIXES.md:

    FIX-[N]
    Fixes: ISSUE-[N]
    Files changed: [list]
    Summary: [what changed]
    Rationale: [why this approach, not alternatives]
    Verified by: [exact commands + summarized output]

  7. Update ISSUE-[N] to FIXED. Update matrix row.

  If a bug cannot be fixed after 3 distinct attempts:
  - Mark ISSUE-[N] BLOCKED with all attempted approaches documented.
  - Mark matrix row BLOCKED.
  - Move on. Do not spin.
  - If 3+ rows BLOCKED simultaneously, write partial REPORT.md and stop.

  ════════════════════════════════════════
  PHASE 8 — LOOP
  ════════════════════════════════════════

  Return to PHASE 1 unconditionally.

  ════════════════════════════════════════
  EXIT CHECK (checked only when matrix is fully PASS at current depth)
  ════════════════════════════════════════

  Continue looping (generating deeper tests) until:
  - All rows at L4 PASS with zero open CRITICAL/HIGH issues
  - At least 2 clean full passes with no new failures
  - REPORT.md written

  ════════════════════════════════════════
  REPORT — docs/hardening-loop/REPORT.md
  ════════════════════════════════════════

  1. Summary: scenarios tested, issues found, fixed, blocked, by severity
  2. Coverage: each feature area with pass evidence (key curl + response)
  3. Fixes: ISSUE-N → FIX-N with one-line summary each
  4. Knowledge accumulated: notable KNOWLEDGE.md entries that changed test strategy
  5. Residual risks: BLOCKED or LOW open issues with honest assessment
  6. Test evolution: how test depth progressed (L1→L4) across areas
  7. Verdict: READY | READY WITH CAVEATS | NOT READY with rationale

  ════════════════════════════════════════
  STANDING RULES
  ════════════════════════════════════════

  - Never skip a failing test without BLOCKED documentation.
  - Never stop to ask confirmation unless genuinely blocked after 3 fix attempts.
  - Always restart server after code changes. Re-verify health before testing.
  - Every curl logged must be exact and copy-pasteable.
  - Always write the hypothesis before running the test.
  - Always derive new test cases from every response (min 2 per run).
  - Prefer root cause fixes over workarounds.
  - The loop is the job. It does not end prematurely.

  Start now. Begin with PHASE 0.
