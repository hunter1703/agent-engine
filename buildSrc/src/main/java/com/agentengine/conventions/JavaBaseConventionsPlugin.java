package com.agentengine.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public class JavaBaseConventionsPlugin implements Plugin<Project> {
  @Override
  public void apply(final Project project) {
    project.getPluginManager().apply("java");
    project.getPluginManager().apply("com.diffplug.spotless");
    project.getPluginManager().apply("org.kordamp.gradle.jandex");

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
        .getExtensions()
        .configure(
            SpotlessExtension.class,
            spotless -> {
              spotless.java(
                  java ->
                      java
                          .eclipse()
                          .configFile(
                              project
                                  .getRootProject()
                                  .file("config/spotless/eclipse.xml")));
              spotless.format(
                  "misc",
                  misc ->
                      {
                        misc.target("*.md", ".gitignore", "*.yml", "*.yaml");
                        misc.trimTrailingWhitespace();
                        misc.endWithNewline();
                      });
            });

    configureTestDependencyConstraints(project.getDependencies());

    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(
            task -> {
              task.getOptions().getCompilerArgs().add("--enable-preview");
              task.getOptions().getCompilerArgs().add("-Xlint:unchecked");
            });

    project
        .getTasks()
        .withType(Test.class)
        .configureEach(
            task -> {
              task.useJUnitPlatform();
              task.jvmArgs("--enable-preview");
            });

    project
        .getTasks()
        .withType(JavaExec.class)
        .configureEach(task -> task.jvmArgs("--enable-preview"));

    project.getTasks().named("jar").configure(task -> task.dependsOn("jandex"));

    project.getPluginManager().withPlugin("io.quarkus", applied ->
        project.getTasks().named("quarkusDependenciesBuild").configure(task -> task.dependsOn("jandex")));
  }

  private static void configureTestDependencyConstraints(final DependencyHandler dependencies) {
    dependencies
        .getConstraints()
        .add("testImplementation", "org.assertj:assertj-core:3.25.3");
    dependencies
        .getConstraints()
        .add("testImplementation", "org.mockito:mockito-core:5.11.0");
    dependencies
        .getConstraints()
        .add("testImplementation", "org.junit.jupiter:junit-jupiter:5.9.2");
    dependencies
        .getConstraints()
        .add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.9.2");
  }
}
