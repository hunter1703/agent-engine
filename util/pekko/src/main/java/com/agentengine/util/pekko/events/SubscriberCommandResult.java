package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;

public interface SubscriberCommandResult extends PekkoSerializable {
    record Successful() implements SubscriberCommandResult {}

    record Failed(Throwable cause) implements SubscriberCommandResult {}
}
