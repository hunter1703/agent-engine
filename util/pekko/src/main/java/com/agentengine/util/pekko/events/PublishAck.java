package com.agentengine.util.pekko.events;

import com.agentengine.util.pekko.PekkoSerializable;

public record PublishAck(long sequence) implements PekkoSerializable {
}
