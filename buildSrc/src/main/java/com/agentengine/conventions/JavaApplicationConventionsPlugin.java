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
    configureNettyVersion(project);

    project.getConfigurations().configureEach(configuration -> {
      configuration.exclude(Map.of("group", "org.springframework.boot", "module", "spring-boot-starter-logging"));
      configuration.exclude(Map.of("group", "ch.qos.logback", "module", "logback-classic"));
      configuration.exclude(Map.of("group", "ch.qos.logback", "module", "logback-core"));
      // com.google.adk:google-adk pulls in com.github.docker-java:docker-java, which ships two
      // alternative HTTP transport implementations (-transport-netty, -transport-jersey) — both
      // land on the classpath even though nothing in this codebase references
      // com.github.dockerjava.* at all (confirmed: zero hits repo-wide). The Jersey transport
      // drags in the full Jersey + HK2 dependency-injection framework nobody uses — dead
      // weight on the classpath (slower classloading/startup, larger attack surface) for zero
      // benefit. -transport-netty stays since only its Netty *version* needed pinning (see
      // configureNettyVersion), not its own dependency tree.
      configuration.exclude(Map.of("group", "com.github.docker-java", "module", "docker-java-transport-jersey"));
    });
  }

  private void configureJacksonDependency(final Project project) {
    final VersionCatalog libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
    libs.findLibrary("jackson-databind").ifPresent(jacksonDatabind -> project.getDependencies().add("implementation", jacksonDatabind));
  }

  /**
   * com.google.adk:google-adk pulls in com.github.docker-java:docker-java(-transport-netty),
   * whose own Netty requirement resolves (Gradle's default "highest version wins") above the
   * 4.1.130.Final that quarkus-bom 3.34.0 itself recommends. A BOM's recommended version is
   * only a recommendation under default conflict resolution, so the transitively-pulled 4.2.x
   * wins even though nothing in this codebase asks for it directly — and Netty 4.2 is a real
   * breaking major version, so running against it instead of the 4.1.x Quarkus is actually
   * built and tested against is a genuine latent-bug risk (a NoSuchMethodError/
   * AbstractMethodError waiting to happen the first time Quarkus's own compiled bytecode calls
   * a 4.1.x-specific signature that changed in 4.2.x), not just a native-image concern.
   * Importing Netty's own BOM as an *enforced* platform pins every io.netty:* artifact's
   * version at once — including ones a future dependency might drag along the same way —
   * without hand-pinning each netty-* module individually. Keep this in sync with whatever
   * Netty version quarkus-bom actually recommends (currently 4.1.130.Final for quarkus 3.34.0
   * — see gradle/libs.versions.toml).
   */
  private void configureNettyVersion(final Project project) {
    project.getDependencies()
        .add("implementation", project.getDependencies().enforcedPlatform("io.netty:netty-bom:4.1.130.Final"));
  }
}
