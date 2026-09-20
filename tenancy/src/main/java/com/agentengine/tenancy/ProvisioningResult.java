package com.agentengine.tenancy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisioningResult {

  private static final Logger LOG = LoggerFactory.getLogger(ProvisioningResult.class);

  private List<Step> steps = new ArrayList<>();

  public void step(final String name, final Runnable action) {
    try {
      action.run();
      steps.add(new Step(name, null));
    } catch (final Exception exception) {
      LOG.error("Provisioning step '{}' failed", name, exception);
      steps.add(new Step(name, String.valueOf(exception)));
    }
  }

  public void steps(final String name, final Supplier<ProvisioningResult> provision) {
    try {
      for (final Step step : provision.get().getSteps()) {
        steps.add(new Step(name + "/" + step.getName(), step.getError()));
      }
    } catch (final Exception exception) {
      LOG.error("Provisioning '{}' failed", name, exception);
      steps.add(new Step(name, String.valueOf(exception)));
    }
  }

  public boolean succeeded() {
    return steps.stream().noneMatch(step -> step.getError() != null);
  }

  public List<Step> getSteps() {
    return steps;
  }

  public void setSteps(final List<Step> steps) {
    this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
  }

  public static class Step {

    private String name;
    private String error;

    public Step() {}

    public Step(final String name, final String error) {
      this.name = name;
      this.error = error;
    }

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getError() {
      return error;
    }

    public void setError(final String error) {
      this.error = error;
    }
  }
}
