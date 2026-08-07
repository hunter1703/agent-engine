package com.agentengine.chaos.core.injection;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.FaultParameters;
import com.agentengine.chaos.api.fault.LlmProviderLatencyParameters;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Injects {@link FaultType#LLM_PROVIDER_UNAVAILABLE}, {@link FaultType#LLM_PROVIDER_LATENCY}, and
 * {@link FaultType#CONNECTOR_FAILURE} faults by registering WireMock stub mappings against an
 * embedded (integration test) or remote (staging) WireMock instance. In staging, the same
 * endpoints are expected to sit behind a Toxiproxy proxy instead — per design.md, this class
 * covers the WireMock delivery mechanism only.
 *
 * <h2>How the target endpoint is identified</h2>
 *
 * {@link FaultParameters} has no dedicated parameter type for {@code LLM_PROVIDER_UNAVAILABLE} or
 * {@code CONNECTOR_FAILURE} — both faults are binary (the endpoint is either down or it isn't;
 * there is no tunable beyond "which endpoint"), so no fault-specific parameter record is needed.
 * Instead, {@link TargetSelector#service()} is reused to carry a WireMock URL regex (the same
 * syntax passed to {@link WireMock#urlMatching(String)}) identifying which requests the stub
 * should intercept, e.g. {@code /v1/chat/completions} for an LLM provider or
 * {@code /connectors/.*} for an outbound connector. {@code namespace} and {@code podLabels} are
 * ignored by this injector (they are meaningful only to the Kubernetes-backed injectors);
 * {@code entityId} is unused here since a stub mapping is not scoped to a single session.
 */
public final class LlmProviderFaultInjector implements FaultInjector {

    /** Status returned by {@link FaultType#LLM_PROVIDER_LATENCY} stubs: the call still succeeds, just slowly. */
    private static final int LATENCY_RESPONSE_STATUS = 200;

    private final WireMock wireMock;

    public LlmProviderFaultInjector(final WireMock wireMock) {
        this.wireMock = wireMock;
    }

    @Override
    public CompletionStage<String> injectFault(
            final FaultType faultType,
            final TargetSelector target,
            final FaultParameters parameters,
            final BlastRadius blastRadius) {
        final MappingBuilder mapping =
                any(urlMatching(target.service())).willReturn(responseFor(faultType, parameters));
        final StubMapping stubMapping = wireMock.register(mapping);
        return CompletableFuture.completedFuture(stubMapping.getId().toString());
    }

    @Override
    public CompletionStage<Void> removeFault(final String faultId) {
        wireMock.removeStubMapping(UUID.fromString(faultId));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean supports(final FaultType faultType) {
        return faultType == FaultType.LLM_PROVIDER_UNAVAILABLE
                || faultType == FaultType.LLM_PROVIDER_LATENCY
                || faultType == FaultType.CONNECTOR_FAILURE;
    }

    private static ResponseDefinitionBuilder responseFor(final FaultType faultType, final FaultParameters parameters) {
        return switch (faultType) {
            case LLM_PROVIDER_UNAVAILABLE -> aResponse().withStatus(503);
            case LLM_PROVIDER_LATENCY -> {
                final LlmProviderLatencyParameters latencyParameters = (LlmProviderLatencyParameters) parameters;
                yield aResponse().withStatus(LATENCY_RESPONSE_STATUS).withFixedDelay((int)
                        latencyParameters.latency().toMillis());
            }
            case CONNECTOR_FAILURE -> aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
            default -> throw new IllegalArgumentException("LlmProviderFaultInjector does not support " + faultType);
        };
    }
}
