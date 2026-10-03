package com.agentengine.util.tenancy;

/**
 * Ids of the roles seeded for every customer, from {@code roles.json} in the tenancy service's
 * resources.
 */
public interface StandardRole {
  String READER = "reader";
  String EDITOR = "editor";

  /**
   * Every permission: on the one entity it is mapped on, everything but CREATE, so it is what
   * whoever an entity is made for gets; on every asset of a class, CREATE too.
   */
  String MANAGER = "manager";
}
