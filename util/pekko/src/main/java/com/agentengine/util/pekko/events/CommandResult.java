package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;

public interface CommandResult extends PekkoSerializable {
    record Completed() implements CommandResult {}

    record Failed(Throwable cause) implements CommandResult {
    }
}
