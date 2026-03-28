package com.agentengine.conventions;

import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;

public class JavaApplicationConventionsPlugin extends BaseJavaConventionsPlugin implements Plugin<Project> {
  @Override
  public void apply(final Project project) {
    project.getPluginManager().apply("io.quarkus");

    configureCommonFunctionality(project);
    configureJacksonDependency(project);

    project.getConfigurations().configureEach(configuration -> {
      configuration.exclude(Map.of("group", "org.springframework.boot", "module", "spring-boot-starter-logging"));
      configuration.exclude(Map.of("group", "ch.qos.logback", "module", "logback-classic"));
      configuration.exclude(Map.of("group", "ch.qos.logback", "module", "logback-core"));
    });
  }

  private void configureJacksonDependency(final Project project) {
    final VersionCatalog libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
    libs.findLibrary("jackson-databind").ifPresent(jacksonDatabind -> project.getDependencies().add("implementation", jacksonDatabind));
  }
}
