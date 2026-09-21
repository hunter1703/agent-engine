package com.agentengine.tenancy;

import java.util.ArrayList;
import java.util.List;

public class ProvisioningResult {

  private List<Step> steps = new ArrayList<>();

  public ProvisioningResult() {}

  public ProvisioningResult(final List<Step> steps) {
    setSteps(steps);
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
