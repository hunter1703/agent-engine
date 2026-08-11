package com.agentengine.util.ms.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a microservice exposed via gRPC.
 *
 * <p>The {@link #value()} identifies which server process hosts this service (e.g. {@code
 * "agent"}). All services running on the same server share the same value, allowing endpoint
 * resolution to look up a single config entry per server.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MicroService {

    /** The server ID that hosts this service (e.g. {@code "agent"}). */
    String value();
}
