package com.agentengine.util.common.builder.annotations;

/** Access levels supported by the builder definition contract. */
public enum UiAccessLevel {
    /** The field is omitted from the builder for the current mode. */
    HIDDEN,
    /** The field is visible but cannot be changed. */
    READ_ONLY,
    /** The field is visible and can be changed. */
    EDITABLE,
    /** The field is visible and must be provided. */
    REQUIRED
}
