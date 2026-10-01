package com.agentengine.tenancy.core.rbac;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.tasks.Task;
import com.agentengine.util.tasks.TaskStatus;
import java.util.Map;
import java.util.Set;

/**
 * A named set of permissions per asset class. A role is also a {@link Task} of its own partition:
 * written {@code PENDING} whenever it is written whole with different permissions, it is done once
 * every mapping that uses it has been marked pending again.
 */
@Index(def = "{'status': 1, 'updatedTime': 1}", name = "pending_lookup")
public class Role extends BaseEntity implements Task {

  /** The task type of a role: marking every mapping that uses it pending again. */
  public static final String TASK_TYPE = "Role";

  private String name;
  private boolean standard;

  private Map<String, Set<String>> assetClassVsPermissions;
  private String status;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public boolean isStandard() {
    return standard;
  }

  public void setStandard(final boolean standard) {
    this.standard = standard;
  }

  public Map<String, Set<String>> getAssetClassVsPermissions() {
    return CollectionUtils.nullSafeMap(assetClassVsPermissions);
  }

  public void setAssetClassVsPermissions(final Map<String, Set<String>> assetClassVsPermissions) {
    this.assetClassVsPermissions = assetClassVsPermissions;
  }

  @Override
  public String getStatus() {
    return status;
  }

  @Override
  public void setStatus(final String status) {
    this.status = status;
  }

  /** A role written whole is pending, since it may grant different permissions. */
  @Override
  public void cleanContextualFields() {
    super.cleanContextualFields();
    status = TaskStatus.PENDING.name();
  }

  /**
   * Also carries over the stored role's status when this one grants the same permissions, so a
   * write that changes nothing they grant, such as a rename, does not process the role again.
   */
  @Override
  public void copyContextualFieldsFrom(final BaseEntity existing) {
    super.copyContextualFieldsFrom(existing);
    if (existing instanceof final Role stored
        && stored.getAssetClassVsPermissions().equals(getAssetClassVsPermissions())) {
      status = stored.getStatus();
    }
  }

  @Override
  public String taskType() {
    return TASK_TYPE;
  }

  @Override
  public String partitionId() {
    return getId();
  }
}
