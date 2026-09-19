package com.agentengine.conventions;

import java.util.concurrent.Callable;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.TaskTriggersConfig;

/**
 * Repackages dependency jars. Each {@code repackagedJars { register('name') { ... } }} entry builds
 * a copy of the dependency's jar with the entries it excludes removed, adds that copy to {@code
 * implementation}, and makes this project's {@code jar} depend on building it, so anything that uses
 * the project builds the copy first. The dependency's own transitive dependencies are not taken,
 * so the project declares those itself.
 */
public class RepackagedJarsPlugin implements Plugin<Project> {

  @Override
  public void apply(final Project project) {
    project.getPluginManager().apply(JavaPlugin.class);
    final NamedDomainObjectContainer<RepackagedJar> jars =
        project.getObjects().domainObjectContainer(RepackagedJar.class);
    project.getExtensions().add("repackagedJars", jars);
    jars.all(jar -> repackage(project, jar));
  }

  private static void repackage(final Project project, final RepackagedJar repackagedJar) {
    final Configuration original =
        project
            .getConfigurations()
            .create(
                repackagedJar.getName() + "Original",
                configuration -> {
                  configuration.setTransitive(false);
                  configuration.setCanBeConsumed(false);
                });
    original.getDependencies().addLater(repackagedJar.getDependency());

    final TaskProvider<Jar> jar =
        project
            .getTasks()
            .register(
                repackagedJar.getName() + "Repackaged",
                Jar.class,
                task -> {
                  task.getArchiveBaseName().set(repackagedJar.getName() + "-repackaged");
                  task.from(
                      (Callable<Object>) () -> project.zipTree(original.getSingleFile()),
                      copy -> {
                        if (!repackagedJar.getIncludes().get().isEmpty()) {
                          copy.include(repackagedJar.getIncludes().get());
                        }
                        copy.exclude(repackagedJar.getExcludes().get());
                      });
                });

    project.getDependencies().add("implementation", project.files(jar));
    project.getTasks().named("jar").configure(task -> task.dependsOn(jar));
    runBeforeIdeSync(project, jar);
  }

  /** IntelliJ resolves the repackaged jar during Gradle sync, so it is built before each sync. */
  private static void runBeforeIdeSync(final Project project, final TaskProvider<Jar> jar) {
    final Project root = project.getRootProject();
    root.getPluginManager().apply("idea");
    root.getPluginManager().apply("org.jetbrains.gradle.plugin.idea-ext");
    final IdeaModel idea = root.getExtensions().getByType(IdeaModel.class);
    final ProjectSettings settings =
        ((ExtensionAware) idea.getProject()).getExtensions().getByType(ProjectSettings.class);
    final TaskTriggersConfig triggers =
        ((ExtensionAware) settings).getExtensions().getByType(TaskTriggersConfig.class);
    triggers.beforeSync(jar);
  }
}
