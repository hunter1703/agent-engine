package com.agentengine.util.common;

/** Utilities for reading runtime environment context. */
public final class EnvUtils {

  private static final String HOSTNAME_ENV = "HOSTNAME";
  private static final String POD_IP_ENV = "POD_IP";
  private static final String PEKKO_CLUSTER_ENV = "PEKKO_CLUSTER";
  private static final String ENVIRONMENT_ENV = "ENVIRONMENT";
  private static final String TIER_ENV = "TIER";

  private EnvUtils() {}

  /**
   * Returns the hostname of the current process, read from the {@code HOSTNAME} environment
   * variable. Returns {@code null} if the variable is not set.
   */
  public static String getHostname() {
    return System.getenv(HOSTNAME_ENV);
  }

  /**
   * Returns the IP address of the current pod, read from the {@code POD_IP} environment variable
   * (usually injected via the Kubernetes Downward API). Returns {@code null} if the variable is not
   * set or is empty.
   */
  public static String getPodIp() {
    return System.getenv(POD_IP_ENV);
  }

  /**
   * Returns the Pekko cluster this pod joins, read from the {@code PEKKO_CLUSTER} environment
   * variable. Returns {@code null} if the variable is not set — that is, this service does not host
   * actors.
   */
  public static String getPekkoCluster() {
    return System.getenv(PEKKO_CLUSTER_ENV);
  }

  /**
   * Returns the deployment environment this pod runs in (e.g. {@code local}, {@code staging}), read
   * from the {@code ENVIRONMENT} environment variable. Returns {@code null} if not set.
   */
  public static String getEnvironment() {
    return System.getenv(ENVIRONMENT_ENV);
  }

  /**
   * Returns the deployment tier this pod runs as (e.g. {@code local}), read from the {@code TIER}
   * environment variable. Returns {@code null} if not set.
   */
  public static String getTier() {
    return System.getenv(TIER_ENV);
  }
}
