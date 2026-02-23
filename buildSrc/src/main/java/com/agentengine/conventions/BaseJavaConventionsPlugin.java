package com.agentengine.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.plugins.JavaPluginExtension;

import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public abstract class BaseJavaConventionsPlugin {

    protected void configureCommonFunctionality(Project project) {
        project.getPluginManager().apply("com.diffplug.spotless");
        project.getPluginManager().apply("org.kordamp.gradle.jandex");

        // Configure Java toolchain
        project.getExtensions().configure(JavaPluginExtension.class, extension -> extension.getToolchain()
                .getLanguageVersion()
                .set(JavaLanguageVersion.of(25)));

        // Configure code formatting
        project.getExtensions().configure(SpotlessExtension.class, spotless -> {
            spotless.java(java -> {
                java.target("src/main/java/**/*.java", "src/test/java/**/*.java");
                java.googleJavaFormat();
                java.removeUnusedImports();
            });

            spotless.format("misc", misc -> {
                misc.target("*.md", ".gitignore", "*.yml", "*.yaml");
                misc.trimTrailingWhitespace();
                misc.endWithNewline();
            });
        });

        // Apply common compiler options
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getCompilerArgs().add("--enable-preview");
            task.getOptions().getCompilerArgs().add("-Xlint:unchecked");
        });

        // Apply common test configurations
        project.getTasks().withType(Test.class).configureEach(task -> {
            task.useJUnitPlatform();
            task.jvmArgs("--enable-preview");
        });

        project.getPluginManager().withPlugin("io.quarkus", applied -> project.getTasks()
                .withType(Test.class)
                .configureEach(task -> task.systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")));

        final VersionCatalog libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
        project.getDependencies().add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher")
                .orElseThrow(() -> new IllegalStateException("Missing junit-platform-launcher catalog entry")));
        project.getDependencies().add("testRuntimeOnly", libs.findLibrary("junit-jupiter-engine")
                .orElseThrow(() -> new IllegalStateException("Missing junit-jupiter-engine catalog entry")));

        // Apply preview flag to JavaExec tasks
        project.getTasks().withType(JavaExec.class).configureEach(task -> task.jvmArgs("--enable-preview"));

        // Ensure jandex runs before jar creation and javadoc
        project.getTasks().named("jar").configure(task -> task.dependsOn("jandex"));
        project.getTasks().withType(org.gradle.api.tasks.javadoc.Javadoc.class).configureEach(task -> task.dependsOn("jandex"));

        // For Quarkus projects, ensure jandex runs before quarkus dependencies build
        project.getPluginManager().withPlugin("io.quarkus", applied -> project.getTasks()
                .named("quarkusDependenciesBuild").configure(task -> task.dependsOn("jandex")));

    }

}
