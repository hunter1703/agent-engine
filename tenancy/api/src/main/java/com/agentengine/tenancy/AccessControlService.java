package com.agentengine.tenancy;

import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.tenancy.AclService;

/**
 * The tenancy service's {@link AclService}, which also submits again the current customer's role
 * and role mapping tasks left pending for longer than a run normally takes: those whose submission
 * was lost, or whose run failed.
 */
@MicroService("tenancy")
public interface AccessControlService extends AclService {

  /** Submits again every role mapping on one asset left pending for too long. */
  void resubmitStaleRoleMappings();

  /** Submits again every role left pending for too long. */
  void resubmitStaleRoles();
}
