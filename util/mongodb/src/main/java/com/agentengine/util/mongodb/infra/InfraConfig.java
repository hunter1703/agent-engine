package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.beans.BaseEntity;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public abstract class InfraConfig extends BaseEntity {
    private String type;

    protected InfraConfig(final String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }
}
