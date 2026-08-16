package com.agentengine.chaos.api.fault;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "parametersType")
@JsonSubTypes({
  @JsonSubTypes.Type(PodKillParameters.class),
  @JsonSubTypes.Type(NetworkLatencyParameters.class),
  @JsonSubTypes.Type(NetworkPartitionParameters.class),
  @JsonSubTypes.Type(PacketLossParameters.class),
  @JsonSubTypes.Type(CpuStressParameters.class),
  @JsonSubTypes.Type(MemoryStressParameters.class),
  @JsonSubTypes.Type(DiskStressParameters.class),
  @JsonSubTypes.Type(DatabaseFailureParameters.class),
  @JsonSubTypes.Type(QdrantFailureParameters.class),
  @JsonSubTypes.Type(LlmProviderLatencyParameters.class),
  @JsonSubTypes.Type(MessageDelayParameters.class),
  @JsonSubTypes.Type(MessageDropParameters.class)
})
public sealed interface FaultParameters
    permits PodKillParameters,
        NetworkLatencyParameters,
        NetworkPartitionParameters,
        PacketLossParameters,
        CpuStressParameters,
        MemoryStressParameters,
        DiskStressParameters,
        DatabaseFailureParameters,
        QdrantFailureParameters,
        LlmProviderLatencyParameters,
        MessageDelayParameters,
        MessageDropParameters {}
