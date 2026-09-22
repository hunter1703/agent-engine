package com.agentengine.util.ms.client;

import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.ServerType;
import io.grpc.ManagedChannelBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MicroServiceChannelProvider
    extends InfraClientFactory<
        MicroServiceClientInfraConfig, MicroServiceServerInfraConfig, Channel> {

  private static final Logger LOG = LoggerFactory.getLogger(MicroServiceChannelProvider.class);

  @Inject
  public MicroServiceChannelProvider(
      final InfraConfigService infraConfigService, final DistributedCacheManager cacheManager) {
    super(infraConfigService, cacheManager, ServerType.MICROSERVICE_SERVER);
  }

  public Channel getForService(final int customerId, final String service) {
    return get(
        getOrCreate(
            MicroServiceUtils.clientId(customerId, service),
            () ->
                MicroServiceUtils.clientConfig(
                    customerId, service, MicroServiceUtils.defaultServerId(service))));
  }

  @Override
  protected Channel create(final MicroServiceServerInfraConfig serverConfig) {
    LOG.debug(
        "Opening channel to server '{}': {}:{}",
        serverConfig.getServerId(),
        serverConfig.getHost(),
        serverConfig.getPort());
    // dns:/// + round_robin: the Service is headless (see app-base/templates/service.yaml), so DNS
    // resolves to every backing pod's own IP rather than one virtual ClusterIP, letting the
    // channel hold a connection per pod and spread calls across all of them.
    return new Channel(
        ManagedChannelBuilder.forTarget(
                "dns:///" + serverConfig.getHost() + ":" + serverConfig.getPort())
            // TODO: check
            .usePlaintext()
            .defaultLoadBalancingPolicy("round_robin")
            .maxInboundMessageSize(MicroServiceServerInfraConfig.MAX_INBOUND_MESSAGE_SIZE)
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build());
  }
}
