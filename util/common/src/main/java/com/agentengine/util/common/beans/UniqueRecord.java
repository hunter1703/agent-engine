package com.agentengine.util.common.beans;

import java.util.UUID;

public class UniqueRecord<T> extends BaseEntity {
    private T record;

    public UniqueRecord() {
    }

    public UniqueRecord(T record) {
        super(UUID.randomUUID().toString());
        this.record = record;
    }

    public T getRecord() {
        return record;
    }

    public void setRecord(final T record) {
        this.record = record;
    }
}
