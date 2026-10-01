package com.agentengine.util.common.beans;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class BaseEntity {
  public static final String FIELD_ID = "id";
  public static final String FIELD_CREATED_TIME = "createdTime";
  public static final String FIELD_UPDATED_TIME = "updatedTime";
  public static final String FIELD_VERSION = "version";
  public static final String FIELD_CREATED_BY = "createdBy";
  public static final String FIELD_TAGS = "tags";
  public static final String FIELD_ACL = "acl";
  public static final String FIELD_ACL_GRANTS = FIELD_ACL + "." + Acl.FIELD_GRANTS;
  public static final String FIELD_ACL_VERSION = FIELD_ACL + "." + Acl.FIELD_VERSION;

  /** The fields the store maintains rather than the entity's author. */
  public static final Set<String> CONTEXTUAL_FIELDS =
      Set.of(FIELD_CREATED_TIME, FIELD_UPDATED_TIME, FIELD_VERSION, FIELD_CREATED_BY, FIELD_ACL);

  private String id;
  private long createdTime;
  private long updatedTime;
  private long version = 0;
  private String createdBy;
  private List<String> tags;

  private Acl acl = Acl.EMPTY;

  public BaseEntity() {}

  public BaseEntity(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public long getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(final long createdTime) {
    this.createdTime = createdTime;
  }

  public long getUpdatedTime() {
    return updatedTime;
  }

  public void setUpdatedTime(final long updatedTime) {
    this.updatedTime = updatedTime;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(final long version) {
    this.version = version;
  }

  /**
   * The principal that created the entity. A record only: who may access the entity is decided by
   * its grants, never by this.
   */
  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(final String createdBy) {
    this.createdBy = createdBy;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(final List<String> tags) {
    this.tags = tags;
  }

  /**
   * The access list, calculated by the tenancy service from the entity's role mappings. Unused by
   * entities that don't need permissioning.
   */
  public Acl getAcl() {
    return acl;
  }

  public void setAcl(final Acl acl) {
    this.acl = acl == null ? Acl.EMPTY : acl;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    final BaseEntity that = (BaseEntity) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  public void cleanContextualFields() {
    createdTime = 0;
    updatedTime = 0;
    version = 0;
    createdBy = null;
    acl = Acl.EMPTY;
  }

  /** Carries over the store-maintained fields of the stored entity this one replaces, if any. */
  public void copyContextualFieldsFrom(final BaseEntity existing) {
    if (existing != null) {
      setCreatedTime(existing.getCreatedTime());
      setVersion(existing.getVersion());
      setCreatedBy(existing.getCreatedBy());
      setAcl(existing.getAcl());
    }
  }
}
