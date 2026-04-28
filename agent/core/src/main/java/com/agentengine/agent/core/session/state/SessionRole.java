package com.agentengine.agent.core.session.state;

import com.agentengine.util.pekko.PekkoSerializable;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionRole.Root.class, name = "Root"),
    @JsonSubTypes.Type(value = SessionRole.Child.class, name = "Child")
})
public interface SessionRole extends PekkoSerializable {

    record Root() implements SessionRole {}

    record Child(String rootSessionId, String parentSessionId, String parentAgentId) implements SessionRole {
        public Child {
            if (rootSessionId == null || parentSessionId == null || parentAgentId == null) {
                throw new IllegalArgumentException("Child topology fields must not be null");
            }
        }
    }
}
