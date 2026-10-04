package com.agentengine.util.tenancy;

/**
 * Ids of the roles seeded for every customer, from {@code roles.json} in the tenancy service's
 * resources.
 */
public interface StandardRole {
  String READER = "reader";
  String EDITOR = "editor";
  String MANAGER = "manager";
}
