package com.agentengine.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class BaseJavaConventionsPlugin {

  private static final String INTEGRATION_TEST_SOURCE_SET_NAME = "integrationTest";
  private static final String INTEGRATION_TEST_TASK_NAME = "integrationTest";

  protected void configureCommonFunctionality(final Project project) {
    project.getPluginManager().apply("com.diffplug.spotless");
    project.getPluginManager().apply("org.kordamp.gradle.jandex");

    project
        .getExtensions()
        .configure(
            JavaPluginExtension.class,
            extension ->
                extension.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(25)));

    project.getExtensions().configure(SpotlessExtension.class, configureSpotless(project));
    configureCompilerOptions(project);
    configureTestTasks(project);
    configureIntegrationTestLane(project);
    configureCommonTestRuntimeDependencies(project);
    configureJavaExecTasks(project);
    configureJandexOrdering(project);
  }

  private static Action<SpotlessExtension> configureSpotless(final Project project) {
    return spotless -> {
      spotless.java(
          java -> {
            java.target(
                "src/main/java/**/*.java",
                "src/test/java/**/*.java",
                "src/integrationTest/java/**/*.java");
            java.eclipse().configFile(project.getRootProject().file("configs/spotless/eclipse.xml"));
            java.removeUnusedImports();
          });

      spotless.format(
          "misc",
          misc -> {
            misc.target("*.md", ".gitignore", "*.yml", "*.yaml");
            misc.trimTrailingWhitespace();
            misc.endWithNewline();
          });
    };
  }

  private static void configureCompilerOptions(final Project project) {
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(
            task -> {
              task.getOptions().getCompilerArgs().add("--enable-preview");
              task.getOptions().getCompilerArgs().add("-Xlint:unchecked");
            });
  }

  private static void configureTestTasks(final Project project) {
    project
        .getTasks()
        .named(
            JavaPlugin.TEST_TASK_NAME,
            Test.class,
            task -> {
              task.useJUnitPlatform();
              task.jvmArgs("--enable-preview");
              task.include("**/*Test.class");
              task.exclude("**/*IT.class", "**/*IntegrationTest.class");
            });

    project
        .getPluginManager()
        .withPlugin(
            "io.quarkus",
            applied ->
                project
                    .getTasks()
                    .withType(Test.class)
                    .configureEach(
                        task ->
                            task.systemProperty(
                                "opts",
                                "--enable-preview -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dfile.encoding=UTF-8")));
  }

  private static void configureIntegrationTestLane(final Project project) {
    project
        .getPluginManager()
        .withPlugin(
            "io.quarkus",
            ignored -> setupIntegrationTestLane(project, false));

    project.afterEvaluate(
        ignored -> {
          if (!project.getPluginManager().hasPlugin("io.quarkus")) {
            setupIntegrationTestLane(project, true);
          }
        });
  }

  private static void setupIntegrationTestLane(
      final Project project, final boolean configureClasspaths) {
    final JavaPluginExtension extension =
        project.getExtensions().getByType(JavaPluginExtension.class);
    final SourceSetContainer sourceSets = extension.getSourceSets();

    final SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    final SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
    final SourceSet integrationTestSourceSet =
        sourceSets.maybeCreate(INTEGRATION_TEST_SOURCE_SET_NAME);

    integrationTestSourceSet.getJava().srcDir("src/integrationTest/java");
    integrationTestSourceSet.getResources().srcDir("src/integrationTest/resources");

    if (configureClasspaths) {
      integrationTestSourceSet.setCompileClasspath(
          integrationTestSourceSet
              .getCompileClasspath()
              .plus(mainSourceSet.getOutput())
              .plus(testSourceSet.getOutput()));
      integrationTestSourceSet.setRuntimeClasspath(
          integrationTestSourceSet
              .getRuntimeClasspath()
              .plus(mainSourceSet.getOutput())
              .plus(testSourceSet.getOutput()));

      project
          .getConfigurations()
          .named(integrationTestSourceSet.getImplementationConfigurationName())
          .configure(
              configuration ->
                  configuration.extendsFrom(
                      project
                          .getConfigurations()
                          .getByName(testSourceSet.getImplementationConfigurationName())));
      project
          .getConfigurations()
          .named(integrationTestSourceSet.getRuntimeOnlyConfigurationName())
          .configure(
              configuration ->
                  configuration.extendsFrom(
                      project
                          .getConfigurations()
                          .getByName(testSourceSet.getRuntimeOnlyConfigurationName())));
    }

    final VersionCatalog libs =
        project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
    project
        .getDependencies()
        .add(
            "integrationTestRuntimeOnly",
            libs.findLibrary("junit-platform-launcher")
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Missing junit-platform-launcher catalog entry")));
    project
        .getDependencies()
        .add(
            "integrationTestRuntimeOnly",
            libs.findLibrary("junit-jupiter-engine")
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Missing junit-jupiter-engine catalog entry")));

    if (project.getTasks().findByName(INTEGRATION_TEST_TASK_NAME) != null) {
      project
          .getTasks()
          .named(
              INTEGRATION_TEST_TASK_NAME,
              Test.class,
              task -> {
                task.setTestClassesDirs(integrationTestSourceSet.getOutput().getClassesDirs());
                task.setClasspath(integrationTestSourceSet.getRuntimeClasspath());
                task.useJUnitPlatform();
                task.jvmArgs("--enable-preview");
                task.shouldRunAfter(project.getTasks().named(JavaPlugin.TEST_TASK_NAME));
                task.include("**/*IT.class", "**/*IntegrationTest.class");
              });
      return;
    }

    project
        .getTasks()
        .register(
            INTEGRATION_TEST_TASK_NAME,
            Test.class,
            task -> {
              task.setGroup("verification");
              task.setDescription("Runs integration tests.");
              task.setTestClassesDirs(integrationTestSourceSet.getOutput().getClassesDirs());
              task.setClasspath(integrationTestSourceSet.getRuntimeClasspath());
              task.useJUnitPlatform();
              task.jvmArgs("--enable-preview");
              task.shouldRunAfter(project.getTasks().named(JavaPlugin.TEST_TASK_NAME));
              task.include("**/*IT.class", "**/*IntegrationTest.class");
            });
  }

  private static void configureCommonTestRuntimeDependencies(final Project project) {
    final VersionCatalog libs =
        project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
    project
        .getDependencies()
        .add(
            "testRuntimeOnly",
            libs.findLibrary("junit-platform-launcher")
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Missing junit-platform-launcher catalog entry")));
    project
        .getDependencies()
        .add(
            "testRuntimeOnly",
            libs.findLibrary("junit-jupiter-engine")
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Missing junit-jupiter-engine catalog entry")));
  }

  private static void configureJavaExecTasks(final Project project) {
    project.getTasks().withType(JavaExec.class).configureEach(task -> task.jvmArgs("--enable-preview"));
  }

  private static void configureJandexOrdering(final Project project) {
    project.getTasks().named("jar").configure(task -> task.dependsOn("jandex"));
    project
        .getTasks()
        .withType(Javadoc.class)
        .configureEach(task -> task.dependsOn("jandex"));

    project
        .getPluginManager()
        .withPlugin(
            "io.quarkus",
            applied ->
                project
                    .getTasks()
                    .named("quarkusDependenciesBuild")
                    .configure(task -> task.dependsOn("jandex")));
  }
}
