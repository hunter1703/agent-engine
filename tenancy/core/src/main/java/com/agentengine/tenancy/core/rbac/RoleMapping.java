package com.agentengine.tenancy.core.rbac;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.HashUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.tasks.Task;
import java.util.List;

/**
 * A principal's roles on one asset, or on every asset when its class and id are null. A mapping on
 * one asset is also a {@link Task} of its asset's partition: written {@code PENDING} with every
 * change to its roles, it is done once its asset's access list is recalculated. A mapping whose
 * roles are all removed stays, empty and pending, until that happens.
 */
@Index(def = "{'assetClass': 1, 'assetId': 1}", name = "asset_lookup")
@Index(def = "{'status': 1, 'updatedTime': 1}", name = "pending_lookup")
@Index(def = "{'roleIds': 1}", name = "role_lookup")
@Index(def = "{'principal': 1}", name = "principal_lookup")
public class RoleMapping extends BaseEntity implements Task {

  /** The task type of a mapping on one asset: recalculating its asset's access list. */
  public static final String TASK_TYPE = "AssetAcl";

  public static final String FIELD_PRINCIPAL = "principal";
  public static final String FIELD_ROLE_IDS = "roleIds";
  public static final String FIELD_ASSET_CLASS = "assetClass";
  public static final String FIELD_ASSET_ID = "assetId";

  private String principal;
  private List<String> roleIds;
  private String assetClass;
  private String assetId;
  private String status;

  /** The partition of the asset {@code assetClass}/{@code assetId}: the asset's access list. */
  public static String partitionId(final String assetClass, final String assetId) {
    return assetClass + ID_SEPARATOR + assetId;
  }

  /**
   * The deterministic id of the mapping for {@code principal} in the scope named by {@code
   * assetClass}/{@code assetId} (both {@code null} for roles on every asset).
   */
  public static String id(final String principal, final String assetClass, final String assetId) {
    final StringBuilder key = new StringBuilder();
    for (final String part :
        new String[] {
          principal, StringUtils.getOrDefault(assetClass, ""), StringUtils.getOrDefault(assetId, "")
        }) {
      key.append(part.length()).append(ID_SEPARATOR).append(part);
    }
    return HashUtils.sha256Hex(key.toString());
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(final String principal) {
    this.principal = principal;
  }

  public List<String> getRoleIds() {
    return CollectionUtils.nullSafeList(roleIds);
  }

  public void setRoleIds(final List<String> roleIds) {
    this.roleIds = roleIds;
  }

  public String getAssetClass() {
    return assetClass;
  }

  public void setAssetClass(final String assetClass) {
    this.assetClass = assetClass;
  }

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(final String assetId) {
    this.assetId = assetId;
  }

  @Override
  public String getStatus() {
    return status;
  }

  @Override
  public void setStatus(final String status) {
    this.status = status;
  }

  @Override
  public String taskType() {
    return TASK_TYPE;
  }

  /** The partition of this mapping's asset; null for a mapping on every asset. */
  @Override
  public String partitionId() {
    return assetId == null ? null : partitionId(assetClass, assetId);
  }
}
