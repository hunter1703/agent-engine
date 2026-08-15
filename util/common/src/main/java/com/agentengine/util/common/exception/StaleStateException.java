package com.agentengine.util.common.exception;

public class StaleStateException extends RuntimeException {
    private final String id;
    private final long foundVersion;
    private final long targetVersion;

    public StaleStateException(final String id, final long foundVersion, final long targetVersion) {
        super(buildMessage(id, foundVersion, targetVersion));
        this.id = id;
        this.foundVersion = foundVersion;
        this.targetVersion = targetVersion;
    }

    public String getId() {
        return id;
    }

    public long getTargetVersion() {
        return targetVersion;
    }

    public long getFoundVersion() {
        return foundVersion;
    }

    private static String buildMessage(final String id, final long foundVersion, final long targetVersion) {
        return "Stale state for entity with ID " + id + ": found version=" + foundVersion + ", target version="
                + targetVersion;
    }
}
