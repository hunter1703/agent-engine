package com.agentengine.util.common.beans;

import com.agentengine.util.common.annotations.Indexed;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

public abstract class BaseEntity {
  public static final String FIELD_ID = "id";
  public static final String FIELD_CREATED_TIME = "createdTime";
  public static final String FIELD_UPDATED_TIME = "updatedTime";
  public static final String FIELD_VERSION = "version";
  public static final String FIELD_OWNER_USER_ID = "ownerUserId";
  public static final String FIELD_TAGS = "tags";
  public static final String FIELD_GRANTS = "grants";
  private String id;
  private long createdTime;
  private long updatedTime;
  private long version = 0;
  private Integer ownerUserId;
  private List<String> tags;
  @Indexed private List<String> grants;

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

  public Integer getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(final Integer ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(final List<String> tags) {
    this.tags = tags;
  }

  /**
   * Flattened access-control tokens, each of the form {@code
   * <principalType>/<principalId>/.../<permission>} — e.g. {@code "agent/a1:READ"} or {@code
   * "agent/a1/user/u2:READ"} for a compound principal. Resolved once by whatever creates the
   * entity, not derived at read time: a reader checks access with a single "does any of my own
   * tokens appear in this list" filter, never by resolving grants itself. Unused by entities that
   * don't need permissioning.
   */
  public List<String> getGrants() {
    return grants;
  }

  public void setGrants(final List<String> grants) {
    this.grants = grants;
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

  public void copyContextualFieldsFrom(BiFunction<String, List<String>, BaseEntity> getExisting) {
    final BaseEntity existing =
        getExisting.apply(
            getId(),
            List.of(
                BaseEntity.FIELD_CREATED_TIME,
                BaseEntity.FIELD_OWNER_USER_ID,
                BaseEntity.FIELD_GRANTS));
    if (existing != null) {
      setCreatedTime(existing.getCreatedTime());
      setGrants(existing.getGrants());
      setOwnerUserId(existing.getOwnerUserId());
    }
  }
}
