package com.agentengine.util.common.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Marker interface for all application domain exceptions. Allows generic serialization and
 * deserialization over gRPC.
 */
@JsonIgnoreProperties({"cause", "stackTrace", "localizedMessage", "suppressed"})
public interface ApplicationException {}
