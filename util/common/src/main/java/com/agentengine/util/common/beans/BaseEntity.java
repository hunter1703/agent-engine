package com.agentengine.util.common.beans;

import java.util.Objects;

public abstract class BaseEntity {
    public static final String FIELD_ID = "id";
    public static final String FIELD_CREATED_TIME = "createdTime";
    public static final String FIELD_UPDATED_TIME = "updatedTime";
    public static final String FIELD_VERSION = "version";
    private String id;
    private long createdTime;
    private long updatedTime;
    private long version = 0;

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
}
