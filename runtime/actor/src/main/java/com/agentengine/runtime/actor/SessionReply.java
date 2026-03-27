package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/**
 * Typed reply types for each external SessionActor command.
 * Callers pattern-match on the sealed variants — no generic receipt types.
 */
public final class SessionReply {

    private SessionReply() {}

    public record Initialized() implements PekkoSerializable {}

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

    public sealed interface SendMessageResult extends PekkoSerializable
            permits SendMessageResult.Accepted, SendMessageResult.Rejected {
        record Accepted(ChildRegistry.ChildRunHandle handle) implements SendMessageResult {}
        record Rejected(String reason) implements SendMessageResult {}
    }

    public sealed interface AwaitResult extends PekkoSerializable
            permits AwaitResult.Completed, AwaitResult.Failed, AwaitResult.Parked {
        record Completed(ChildRegistry.ChildRunResult result) implements AwaitResult {}
        record Failed(String reason) implements AwaitResult {}
        /** Session parked — execution will resume automatically when child completes. */
        record Parked() implements AwaitResult {}
    }
}
