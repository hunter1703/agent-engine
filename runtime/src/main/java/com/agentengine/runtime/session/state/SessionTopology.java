package com.agentengine.runtime.session.state;

import com.agentengine.util.pekko.PekkoSerializable;

public record SessionTopology(String sessionId, String agentId, SessionRole role) implements PekkoSerializable {

  public static SessionTopology root(final String agentId, final String sessionId) {
    return new SessionTopology(sessionId, agentId, new SessionRole.Root());
  }

  public static SessionTopology child(final String agentId, final String sessionId, final String rootSessionId,
      final String parentSessionId, final String parentAgentId) {
    return new SessionTopology(sessionId, agentId, new SessionRole.Child(rootSessionId, parentSessionId, parentAgentId));
  }

  public String rootSessionId() {
    return switch (role) {
      case SessionRole.Root _ -> sessionId;
      case SessionRole.Child childRole -> childRole.rootSessionId();
      default -> throw new IllegalStateException("Unknown session role: " + role);
    };
  }

  public String parentSessionId() {
    return switch (role) {
      case SessionRole.Root _ -> null;
      case SessionRole.Child childRole -> childRole.parentSessionId();
      default -> throw new IllegalStateException("Unknown session role: " + role);
    };
  }

  public String parentAgentId() {
    return switch (role) {
      case SessionRole.Root _ -> null;
      case SessionRole.Child childRole -> childRole.parentAgentId();
      default -> throw new IllegalStateException("Unknown session role: " + role);
    };
  }

  public boolean isRoot() {
    return role instanceof SessionRole.Root;
  }

  public interface SessionRole extends PekkoSerializable {
    record Root() implements SessionRole {
    }

    record Child(String rootSessionId, String parentSessionId, String parentAgentId) implements SessionRole {
      public Child {
        if (rootSessionId == null || parentSessionId == null || parentAgentId == null) {
          throw new IllegalArgumentException("Child topology fields must not be null");
        }
      }
    }
  }
}
