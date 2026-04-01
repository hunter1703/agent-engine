package com.agentengine.util.agents.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("DEFAULT")
@BsonDiscriminator(value = "DEFAULT")
public class DefaultAgentConfig extends BaseAgentConfig {
    public DefaultAgentConfig() {
        super(AgentType.DEFAULT);
    }
}
