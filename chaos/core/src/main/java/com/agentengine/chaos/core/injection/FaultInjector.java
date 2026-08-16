package com.agentengine.chaos.core.injection;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.FaultParameters;
import java.util.concurrent.CompletionStage;

/**
 * Injects and removes a specific category of fault. Each implementation owns one delivery mechanism
 * (Chaos Mesh CRDs, Pekko message interception, Toxiproxy, WireMock) and declares which {@link
 * FaultType}s it can handle via {@link #supports}.
 */
public interface FaultInjector {

  /** Injects the fault and returns an opaque fault ID used later to remove it. */
  CompletionStage<String> injectFault(
      FaultType faultType,
      TargetSelector target,
      FaultParameters parameters,
      BlastRadius blastRadius);

  CompletionStage<Void> removeFault(String faultId);

  boolean supports(FaultType faultType);
}
