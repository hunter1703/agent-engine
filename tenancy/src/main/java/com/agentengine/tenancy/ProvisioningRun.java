package com.agentengine.tenancy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisioningRun {

  private static final Logger LOG = LoggerFactory.getLogger(ProvisioningRun.class);

  private final List<ProvisioningResult.Step> steps = new ArrayList<>();

  public void step(final String name, final Runnable action) {
    if (failed()) {
      return;
    }
    try {
      action.run();
      steps.add(new ProvisioningResult.Step(name, null));
    } catch (final Exception exception) {
      LOG.error("Provisioning step '{}' failed", name, exception);
      steps.add(new ProvisioningResult.Step(name, String.valueOf(exception)));
    }
  }

  public void merge(final String name, final Supplier<ProvisioningResult> provisioning) {
    if (failed()) {
      return;
    }
    try {
      for (final ProvisioningResult.Step step : provisioning.get().getSteps()) {
        steps.add(new ProvisioningResult.Step(name + "/" + step.getName(), step.getError()));
      }
    } catch (final Exception exception) {
      LOG.error("Provisioning '{}' failed", name, exception);
      steps.add(new ProvisioningResult.Step(name, String.valueOf(exception)));
    }
  }

  public ProvisioningResult result() {
    return new ProvisioningResult(steps);
  }

  private boolean failed() {
    return steps.stream().anyMatch(step -> step.getError() != null);
  }
}
