package com.agentengine.util.vectordb;

import com.agentengine.util.infra.InfraClientConfig;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "com.agentengine.util.vectordb.VectorClientInfraConfig")
public class VectorClientInfraConfig extends InfraClientConfig {
  public static final String CATEGORY = "VECTOR";
}
