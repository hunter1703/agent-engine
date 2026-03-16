# Coding Agent Improvements

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan
> task-by-task.

**Goal:** Close the feature gaps identified during architectural comparison with a production coding
agent. Six independent improvements are ordered by impact. Each item can be executed in isolation
without affecting the others.

**Context:** Agent-engine is being evolved toward a full coding-agent use case (feature development,
code review, documentation). This plan addresses the gaps that matter most for that scenario:
session recovery, resilient code patching, project-aware instructions, change visibility, tool
execution correctness, and compaction robustness.

**Tech Stack:** Java 25, Quarkus, Google ADK, RxJava3, MongoDB Java Driver 5.x, JUnit 5 + Mockito.

---

## Item 1 — Session Rollback

**Priority:** Critical
**Why:** A coding agent that goes down a wrong path (deletes files, writes broken code, pursues the
wrong approach across multiple turns) currently requires the user to start a fresh session and
re-establish context. Rollback lets the user prune the last N user turns and resume from a known
good state, which is fundamental to productive coding sessions.

**Design:**
Session events in MongoDB are append-only (encrypted JSONL in `SessionInfo.eventsJson`). Rather
than mutating the event log, append a sentinel `RollbackEvent` carrying the target truncation point.
`RunState.buildFrom` and `SessionUtils.toSession` already replay the event list — they need to
honour rollback markers by ignoring events after the rollback boundary. This keeps the full audit
trail while making `buildFrom` produce the correct rolled-back state.

A "user turn boundary" is any non-internal, non-partial event authored by the user. Rolling back
N turns means discarding everything after the Nth-most-recent boundary.

**Files:**
- Create: `engine/api/src/main/java/com/agentengine/engine/api/events/RollbackEvent.java`
- Modify: `engine/src/main/java/com/agentengine/engine/utils/SessionUtils.java` — add
  `rollbackBoundary(events, turns)` and `applyRollbacks(events)`
- Modify: `engine/src/main/java/com/agentengine/engine/utils/RunState.java` — call
  `applyRollbacks` before `buildFrom`
- Modify: `engine/src/main/java/com/agentengine/engine/repository/AgentSessionRepository.java` —
  add `rollback(sessionId, turns)` that appends the sentinel event
- Modify: `interfaces/rest/src/main/java/com/agentengine/interfaces/rest/AgentRestAPI.java` — add
  `POST /v1/agent/session/{sessionId}/rollback?turns={n}` endpoint
- Create: `engine/src/test/java/com/agentengine/engine/utils/SessionUtilsRollbackTest.java`

**Implementation steps:**

### Step 1: Write failing tests

In `SessionUtilsRollbackTest`:

```java
@Test
void shouldRollbackOneUserTurn() {
    // Build event list: [user1, assistant1, user2, assistant2]
    // Rollback 1 turn → effective events: [user1, assistant1]
    List<Event> events = buildEvents(/* user1, assistant1, user2, assistant2 */);
    List<Event> rolled = SessionUtils.applyRollbacks(
        SessionUtils.appendRollbackMarker(events, 1));
    assertThat(rolled).hasSize(2);
    assertThat(lastUserContent(rolled)).isEqualTo("user1");
}

@Test
void shouldComposeRollbacks() {
    // Two sequential rollbacks compose: first roll 1, then roll 1 more
    // Net effect: roll 2 from original
}

@Test
void shouldNotRollbackBeyondFirstTurn() {
    List<Event> events = buildEvents(/* single user turn */);
    List<Event> rolled = SessionUtils.applyRollbacks(
        SessionUtils.appendRollbackMarker(events, 5));
    assertThat(rolled).hasSize(1); // just the first user event
}
```

### Step 2: Add `RollbackEvent`

```java
/** Sentinel event appended to the session log to record a rollback request. */
public record RollbackEvent(int turns, long timestamp) {
    public static final String TYPE = "rollback";
}
```

Serialise as a regular ADK `Event` with a reserved author (`__system__`) and a function call
named `__rollback__` with `turns` as the argument — so existing event serialization infra handles it
without schema changes.

### Step 3: Implement `SessionUtils.applyRollbacks`

```java
/**
 * Replays the event list honouring any embedded rollback markers.
 * Markers are processed left-to-right; each one truncates the visible
 * window by moving the effective start forward.
 */
public static List<Event> applyRollbacks(List<Event> events) { ... }

/** Returns the indices of user-turn boundaries (non-internal, non-partial user events). */
private static List<Integer> userTurnBoundaries(List<Event> events) { ... }
```

### Step 4: Wire into `RunState.buildFrom` and REST endpoint

`buildFrom` calls `SessionUtils.applyRollbacks(events)` before any processing.
REST endpoint delegates to `AgentSessionRepository.rollback(sessionId, turns)`.

---

## Item 2 — Context-Anchor Patch Format

**Priority:** High
**Why:** `ApplyPatchTool` uses standard unified diff with line numbers in `@@ -line,count @@`
headers. LLMs generate these numbers based on the file content they read at prompt-build time.
By the time the patch is applied, earlier hunks in the same patch — or prior tool calls — may have
shifted all subsequent line numbers, causing cascading application failures. Context-based anchors
(`@@ some_context_string`) locate the hunk by searching for a distinctive surrounding line,
tolerating line offset drift.

**Design:**
Introduce a second accepted hunk header form alongside the existing unified-diff form:

```
@@ context: funcName(args) {
-    old line
+    new line
```

The parser tries unified-diff first; if `@@ context:` is detected, it searches the file for the
anchor string (exact match, then trimmed match) and applies the hunk relative to that position.
Both formats continue to work — old agents using unified diff are unaffected; new model instructions
can guide the model to prefer the context-anchor form for multi-hunk patches.

**Files:**
- Modify: `engine/src/main/java/com/agentengine/engine/tools/fileops/ApplyPatchTool.java`
- Create: `engine/src/test/java/com/agentengine/engine/tools/fileops/ApplyPatchToolTest.java`

**Implementation steps:**

### Step 1: Write failing tests

```java
@Test
void shouldApplyPatchWithContextAnchor() {
    String original = "public void foo() {\n    int x = 1;\n    return x;\n}\n";
    String patch = "@@ context: public void foo() {\n-    int x = 1;\n+    int x = 42;\n";
    String result = ApplyPatchTool.applyContextAnchorPatch(original, patch);
    assertThat(result).contains("int x = 42;");
}

@Test
void shouldFallBackToUnifiedDiffWhenNoContextMarker() { ... }

@Test
void shouldReturnErrorWhenAnchorNotFound() { ... }

@Test
void shouldHandleAmbiguousAnchor() {
    // Same context line appears twice — should return a descriptive error
}
```

### Step 2: Extend `parseHunks` to detect format

In `parseHunks`, check if the hunk header matches `@@ context:` (case-insensitive). If so, record
a `PatchHunk` with `anchorText` set and `oldStart = -1` (sentinel for "locate by search").

### Step 3: Implement `applyContextAnchorHunk`

```java
private HunkApplication applyContextAnchorHunk(List<String> lines, PatchHunk hunk) {
    // 1. Search lines for exact match of anchor text
    // 2. If not found, try trimmed comparison
    // 3. If still not found, or found multiple times, return descriptive error
    // 4. Apply removal/addition relative to found index
}
```

### Step 4: Update tool descriptor instructions

Extend `DESCRIPTOR.description()` to explain both formats and recommend context-anchor form
for multi-hunk patches or when line numbers may be unreliable.

---

## Item 3 — Per-Directory Instruction Files

**Priority:** High
**Why:** Different parts of a repository have different conventions, owners, and risk profiles. A
single agent-level system prompt cannot encode "this directory is the payment module — always
validate PCI constraints" alongside "this is the test harness — mocking is allowed here." Walking
the directory tree and collecting instruction files (e.g. `AGENT.md`) at each level is the natural
way to deliver this context.

**Design:**
Add an optional `workingDirectory` field to `BaseAgentConfig`. When set, a new
`ProjectInstructionLoader` walks from that directory up to the project root (first ancestor
containing `.git`), collecting all `AGENT.md` files found, concatenating them root-first so
root-level rules take lowest precedence and directory-level rules override. The combined text is
injected into the agent system prompt before the agent's own instructions.

**Files:**
- Create: `engine/src/main/java/com/agentengine/engine/context/ProjectInstructionLoader.java`
- Modify: `engine/api/src/main/java/com/agentengine/engine/api/beans/config/BaseAgentConfig.java`
  — add `workingDirectory` field
- Modify: `engine/src/main/java/com/agentengine/engine/builders/agent/BaseLlmAgentBuilder.java`
  — prepend loaded instructions to system prompt
- Create: `engine/src/test/java/com/agentengine/engine/context/ProjectInstructionLoaderTest.java`

**Implementation steps:**

### Step 1: Write failing tests

```java
@Test
void shouldCollectInstructionsFromAllAncestorDirectories(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("AGENT.md"), "root rule");
    Path subdir = Files.createDirectories(root.resolve("src/payments"));
    Files.writeString(subdir.resolve("AGENT.md"), "payments rule");
    Files.createDirectory(root.resolve(".git")); // project root marker

    String combined = ProjectInstructionLoader.load(subdir, root);
    assertThat(combined).contains("root rule");
    assertThat(combined).contains("payments rule");
    // root comes first (lowest priority), subdir last (highest priority)
    assertThat(combined.indexOf("root rule")).isLessThan(combined.indexOf("payments rule"));
}

@Test
void shouldStopAtGitRoot() { ... }

@Test
void shouldReturnEmptyWhenNoInstructionFilesExist() { ... }
```

### Step 2: Implement `ProjectInstructionLoader`

```java
public final class ProjectInstructionLoader {
    public static final String DEFAULT_FILENAME = "AGENT.md";

    /** Walks from {@code startDir} up to {@code projectRoot}, collecting instruction files. */
    public static String load(Path startDir, Path projectRoot) { ... }

    /** Locates the project root by walking up to the first directory containing {@code .git}. */
    public static Optional<Path> findProjectRoot(Path startDir) { ... }
}
```

### Step 3: Wire into agent builder

In `BaseLlmAgentBuilder.build()`, if `config.getWorkingDirectory()` is set, call
`ProjectInstructionLoader.load(...)` and prepend the result to the system instruction with a
clear delimiter (`--- Project Instructions ---`).

---

## Item 4 — Turn-Level File Diff Tracking

**Priority:** Medium
**Why:** Users working with a coding agent need to know which files changed during a turn without
inspecting every individual tool call. A per-turn change summary (similar to a commit message with
changed paths) is essential UI feedback, especially when multiple `apply_patch` and shell commands
mutate files across a single multi-step turn.

**Design:**
Add a `modifiedFiles` set to `RunState`. Tools that write to the filesystem register their
modified paths via `RunState.recordFileChange(path)`. At turn completion,
`TurnCompletionResponseProcessor` emits a `TurnDiffEvent` through the AGUI event pipeline. The
REST event stream includes it; clients can render a file-change summary per turn.

**Files:**
- Modify: `engine/src/main/java/com/agentengine/engine/utils/RunState.java` — add
  `recordFileChange`, `consumeModifiedFiles`
- Modify: `engine/src/main/java/com/agentengine/engine/tools/fileops/ApplyPatchTool.java` — call
  `recordFileChange` on success
- Modify: `engine/src/main/java/com/agentengine/engine/tools/shell/ShellCommandTool.java` — best-
  effort path extraction from command; record if deterministic
- Modify: `engine/src/main/java/com/agentengine/engine/agents/processors/TurnCompletionResponseProcessor.java`
  — emit `TurnDiffEvent` if modified files non-empty
- Modify: `engine/api/src/main/java/com/agentengine/engine/api/events/agui/` — add `TurnDiffEvent`
- Modify: `engine/src/main/java/com/agentengine/engine/agents/mappers/AGUIEventMapper.java` — map
  `TurnDiffEvent` to AGUI stream
- Create: `engine/src/test/java/com/agentengine/engine/utils/RunStateTurnDiffTest.java`

**Implementation steps:**

### Step 1: Write failing tests

```java
@Test
void shouldAccumulateFileChangesAcrossToolCalls() {
    RunState state = new RunState();
    state.recordFileChange("src/Foo.java");
    state.recordFileChange("src/Bar.java");
    state.recordFileChange("src/Foo.java"); // duplicate
    assertThat(state.consumeModifiedFiles()).containsExactlyInAnyOrder("src/Foo.java", "src/Bar.java");
    assertThat(state.consumeModifiedFiles()).isEmpty(); // consumed
}
```

### Step 2: Extend `RunState`

```java
private final Set<String> modifiedFiles = new LinkedHashSet<>();

public void recordFileChange(String path) {
    if (StringUtils.isNotBlank(path)) modifiedFiles.add(path);
}

/** Returns modified paths and clears the set. Called once at turn end. */
public Set<String> consumeModifiedFiles() {
    final Set<String> snapshot = Set.copyOf(modifiedFiles);
    modifiedFiles.clear();
    return snapshot;
}
```

### Step 3: Register changes in tools

`ApplyPatchTool`: after `Files.writeString(file, result.newContent())`, call
`RunState.fromContext(toolContext).recordFileChange(filePath)`.

`ShellCommandTool`: extract target paths only from deterministic write commands
(`cp`, `mv`, `tee`, `>` redirect); skip extraction for opaque commands to avoid false positives.

### Step 4: Emit `TurnDiffEvent`

```java
// in TurnCompletionResponseProcessor
Set<String> changed = runState.consumeModifiedFiles();
if (!changed.isEmpty()) {
    eventBus.emit(new TurnDiffEvent(sessionId, agentId, List.copyOf(changed)));
}
```

`TurnDiffEvent` AGUI shape: `{ type: "turn_diff", sessionId, agentId, modifiedFiles: [paths] }`.

---

## Item 5 — Per-Invocation Tool Mutability

**Priority:** Medium
**Why:** The current `ToolExecutionMode` is set at the agent-config level: either all tools run in
parallel or all run sequentially. A read-heavy agent (multiple `read_file`, `grep` calls) forced
into SEQUENTIAL mode loses concurrency unnecessarily. An agent in PARALLEL mode risks concurrent
writes. The correct model is: reads run concurrently; writes serialize against everything.

**Design:**
Add `boolean isMutating(Map<String, Object> args)` to the `Tool` base class (default `false`).
Override in mutating tools. The tool executor acquires a `ReadWriteLock` per execution context:
non-mutating tools take a read lock (concurrent with each other); mutating tools take a write lock
(exclusive). The `ToolExecutionMode` config remains for backwards compatibility but its SEQUENTIAL
value becomes a special case of "all tools are mutating."

**Files:**
- Modify: `engine/plugin/src/main/java/com/agentengine/engine/plugin/tools/Tool.java` — add
  `isMutating(Map<String, Object> args)` with default `false`
- Modify: `engine/src/main/java/com/agentengine/engine/tools/fileops/ApplyPatchTool.java` —
  override `isMutating` → `true`
- Modify: `engine/src/main/java/com/agentengine/engine/tools/shell/ShellCommandTool.java` —
  override `isMutating` → `true` (conservative; shell commands are opaque)
- Modify: `engine/src/main/java/com/agentengine/engine/tools/ToolServiceImpl.java` (or equivalent
  executor) — replace `ToolExecutionMode` fork with `ReadWriteLock` dispatch
- Create: `engine/src/test/java/com/agentengine/engine/tools/ToolMutabilityTest.java`

**Implementation steps:**

### Step 1: Write failing tests

```java
@Test
void shouldRunNonMutatingToolsConcurrently() {
    // Two read_file calls should overlap in time (both acquire read lock)
    // Measure wall time: concurrent run < sum of sequential run times
}

@Test
void shouldSerializeMutatingToolAgainstParallelReads() {
    // apply_patch running concurrently with read_file:
    // the patch must not interleave mid-read
}
```

### Step 2: Add `isMutating` to `Tool`

```java
/**
 * Returns true if this tool call modifies shared state (filesystem, session,
 * external services). Mutating calls are serialized; non-mutating calls may
 * run concurrently.
 */
public boolean isMutating(Map<String, Object> args) {
    return false;
}
```

### Step 3: Update executor dispatch

```java
private static final ReadWriteLock EXEC_LOCK = new ReentrantReadWriteLock();

private Object executeTool(Tool tool, Map<String, Object> args) {
    if (tool.isMutating(args)) {
        EXEC_LOCK.writeLock().lock();
        try { return tool.execute(args); }
        finally { EXEC_LOCK.writeLock().unlock(); }
    } else {
        EXEC_LOCK.readLock().lock();
        try { return tool.execute(args); }
        finally { EXEC_LOCK.readLock().unlock(); }
    }
}
```

The `ReadWriteLock` scope should be per-session, not global — pass it through `RunState` or the
tool execution context so concurrent sessions don't contend.

---

## Item 6 — Compaction Fallback on Summary Model Failure

**Priority:** Low-Medium
**Why:** When `CompactionContextManager.callSummaryModel` fails (model unavailable, timeout, or
the summary call itself exceeds the context window), the current fallback is to pass the full
uncompacted context to the main model. If that context is already over the model's context window,
the main call also fails — silent cascade. The fallback should degrade gracefully by trimming
oldest content until the context fits within the threshold.

**Current behaviour (line 91-92):**
```java
LOG.warn("Compaction failed ... using full context.");
return withSummaryPrefix(existingSummary, contents);
// ↑ May still exceed model's context window
```

**Files:**
- Modify: `engine/src/main/java/com/agentengine/engine/context/CompactionContextManager.java`
- Modify: `engine/src/test/java/com/agentengine/engine/context/CompactionContextManagerTest.java`

**Implementation steps:**

### Step 1: Write failing tests

```java
@Test
void shouldTrimOldestContentWhenSummaryModelFails() {
    // Mock model provider to throw on generateContent
    // Feed contents totalling 2× tokenThreshold
    // Expect result to have total tokens ≤ tokenThreshold
    // Most recent contents preserved; oldest discarded
}

@Test
void shouldPreserveAtLeastMostRecentTurnEvenIfOversized() {
    // Single giant content block exceeding threshold
    // Must not return empty list — preserve last item regardless
}
```

### Step 2: Add `trimToThreshold`

```java
/**
 * Trims oldest content items until estimated tokens fall within
 * {@code maxTokens}. Always preserves at least the most recent item.
 */
private static List<Content> trimToThreshold(List<Content> contents, int maxTokens) {
    if (CollectionUtils.isEmpty(contents)) return List.of();
    final List<Content> mutable = new ArrayList<>(contents);
    while (mutable.size() > 1 && estimateTotalTokens(mutable) > maxTokens) {
        mutable.remove(0);
    }
    return mutable;
}
```

### Step 3: Replace silent fallback

```java
if (StringUtils.isNotBlank(newSummary)) {
    persistSummary(sessionId, agentId, newSummary);
    return withSummaryPrefix(newSummary, recent);
}

// Compaction failed — trim oldest content to stay within threshold
LOG.warn("Compaction failed for agent_id={} session_id={}; trimming oldest content to fit threshold.", agentId, sessionId);
final List<Content> trimmed = trimToThreshold(contents, tokenThreshold);
return withSummaryPrefix(existingSummary, trimmed);
```

---

## Sequencing

Items are independent and can be implemented in any order. Suggested sequence if doing them
together:

1. **Item 6** (compaction fallback) — smallest change, highest safety payoff, good warmup
2. **Item 5** (tool mutability) — pure engine change, no external dependencies
3. **Item 4** (turn diff tracking) — builds naturally after understanding tool execution flow
4. **Item 2** (context-anchor patches) — self-contained, isolated to `ApplyPatchTool`
5. **Item 3** (per-directory instructions) — requires new loader class but well-bounded
6. **Item 1** (session rollback) — most cross-cutting; touches event model, repository, REST API

For Items 1 and 4, coordinate with any active frontend contracts
(`docs/11-builder-contract-for-frontend.md`) before finalising the AGUI event shapes.
