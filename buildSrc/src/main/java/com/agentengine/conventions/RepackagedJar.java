package com.agentengine.conventions;

import javax.inject.Inject;
import org.gradle.api.Named;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * A dependency's jar, copied without the entries it should not contribute: {@code exclude} and
 * {@code include} take Ant-style patterns over the jar's entries, as in a Gradle copy spec.
 */
public abstract class RepackagedJar implements Named {

  private final String name;

  @Inject
  public RepackagedJar(final String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  public abstract Property<MinimalExternalModuleDependency> getDependency();

  public abstract ListProperty<String> getExcludes();

  public abstract ListProperty<String> getIncludes();

  public void exclude(final String... patterns) {
    getExcludes().addAll(patterns);
  }

  public void include(final String... patterns) {
    getIncludes().addAll(patterns);
  }
}
