package com.agentengine.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public class JavaBaseConventionsPlugin implements Plugin<Project> {
  @Override
  public void apply(final Project project) {
    project
        .getExtensions()
        .configure(
            JavaPluginExtension.class,
            extension ->
                extension
                    .getToolchain()
                    .getLanguageVersion()
                    .set(JavaLanguageVersion.of(21)));

    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(task -> task.getOptions().getCompilerArgs().add("--enable-preview"));

    project
        .getTasks()
        .withType(Test.class)
        .configureEach(task -> task.jvmArgs("--enable-preview"));
  }
}
