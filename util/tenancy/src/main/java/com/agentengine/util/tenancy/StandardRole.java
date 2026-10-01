package com.agentengine.util.tenancy;

/**
 * Ids of the roles seeded for every customer, from {@code roles.json} in the tenancy service's
 * resources.
 */
public interface StandardRole {
  String READER = "reader";
  String EDITOR = "editor";
  String MANAGER = "manager";
  String CREATOR = "creator";

  /**
   * Every permission on the one entity it is granted on; given to whoever an entity is made for.
   */
  String OWNER = "owner";
}
