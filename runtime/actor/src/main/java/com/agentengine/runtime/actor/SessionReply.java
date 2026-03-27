package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/**
 * Typed reply types for each external SessionActor command.
 * Callers pattern-match on the sealed variants — no generic receipt types.
 */
public final class SessionReply {

    private SessionReply() {}

    public sealed interface InitializeResult extends PekkoSerializable
            permits InitializeResult.Initialized, InitializeResult.AlreadyInitialized {
        record Initialized() implements InitializeResult {}
        record AlreadyInitialized() implements InitializeResult {}
    }

    public sealed interface StartRunResult extends PekkoSerializable
            permits StartRunResult.RunAccepted, StartRunResult.RunQueued, StartRunResult.Rejected {
        record RunAccepted(String runId) implements StartRunResult {}
        record RunQueued(int position) implements StartRunResult {}
        record Rejected(String reason) implements StartRunResult {}
    }

    public sealed interface ResumeResult extends PekkoSerializable
            permits ResumeResult.Resumed, ResumeResult.Rejected {
        record Resumed(String runId) implements ResumeResult {}
        record Rejected(String reason) implements ResumeResult {}
    }

    public sealed interface SpawnResult extends PekkoSerializable
            permits SpawnResult.ChildSpawned, SpawnResult.Rejected {
        record ChildSpawned(ChildRegistry.ChildRunHandle handle) implements SpawnResult {}
        record Rejected(String reason) implements SpawnResult {}
    }

    public sealed interface SendTaskResult extends PekkoSerializable
            permits SendTaskResult.TaskAccepted, SendTaskResult.Rejected {
        record TaskAccepted(ChildRegistry.ChildRunHandle handle) implements SendTaskResult {}
        record Rejected(String reason) implements SendTaskResult {}
    }

    public sealed interface AwaitResult extends PekkoSerializable
            permits AwaitResult.Completed, AwaitResult.Failed {
        record Completed(ChildRegistry.ChildRunResult result) implements AwaitResult {}
        record Failed(String reason) implements AwaitResult {}
    }
}
