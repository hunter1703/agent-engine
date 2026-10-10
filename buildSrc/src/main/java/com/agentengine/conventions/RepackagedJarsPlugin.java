package com.agentengine.conventions;

import java.util.concurrent.Callable;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;

/**
 * Repackages dependency jars. Each {@code repackagedJars { register('name') { ... } }} entry adds
 * the dependency's classes, minus the entries it excludes, directly into this project's own {@code
 * jar} output (shaded in, not a separate artifact) and onto its {@code compileOnly} classpath so
 * the project's own sources compile against them. Shading into the project's own jar — rather than
 * adding the stripped copy as a separate {@code implementation project.files(...)} classpath entry
 * — is what makes this survive Maven publishing: a consumer that depends on this project's
 * published artifact from a separate build (as opposed to a sibling Gradle project in the same
 * build) only ever resolves the published jar's own contents, never a same-build file-collection
 * dependency, so the stripped classes must physically be inside that jar. The dependency's own
 * transitive dependencies are not taken, so the project declares those itself.
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

    // Compile-time only: the project's own sources see the dependency's classes, but it is never
    // a real dependency of the published artifact — its (stripped) classes are shaded into this
    // project's own jar below instead.
    project.getConfigurations().getByName("compileOnly").getDependencies().addLater(
        repackagedJar.getDependency());

    project
        .getTasks()
        .named("jar", Jar.class)
        .configure(
            task ->
                task.from(
                    (Callable<Object>) () -> project.zipTree(original.getSingleFile()),
                    copy -> {
                      if (!repackagedJar.getIncludes().get().isEmpty()) {
                        copy.include(repackagedJar.getIncludes().get());
                      }
                      copy.exclude(repackagedJar.getExcludes().get());
                    }));
  }
}
