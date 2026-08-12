package com.agentengine.util.common;

/** Utilities for reading runtime environment context. */
public final class EnvUtils {

    private static final String HOSTNAME_ENV = "HOSTNAME";

    private EnvUtils() {}

    /**
     * Returns the hostname of the current process, read from the {@code HOSTNAME} environment
     * variable. Returns {@code null} if the variable is not set.
     */
    public static String getHostname() {
        return System.getenv(HOSTNAME_ENV);
    }

    /**
     * Returns the IP address of the current pod, read from the {@code POD_IP} environment
     * variable (usually injected via the Kubernetes Downward API).
     * Returns {@code null} if the variable is not set or is empty.
     */
    public static String getPodIp() {
        return System.getenv("POD_IP");
    }
}
