package com.agentengine.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import org.gradle.api.Project;
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
                .set(JavaLanguageVersion.of(21)));

        // Configure code formatting
        project.getExtensions().configure(SpotlessExtension.class, spotless -> {
            spotless.java(java -> {
                java.eclipse().configFile(project.getRootProject().file("configs/spotless/eclipse.xml").getAbsolutePath());
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

        // Apply preview flag to JavaExec tasks
        project.getTasks().withType(JavaExec.class).configureEach(task -> task.jvmArgs("--enable-preview"));

        // Ensure jandex runs before jar creation
        project.getTasks().named("jar").configure(task -> task.dependsOn("jandex"));

        // For Quarkus projects, ensure jandex runs before quarkus dependencies build
        project.getPluginManager().withPlugin("io.quarkus", applied -> project.getTasks()
                .named("quarkusDependenciesBuild").configure(task -> task.dependsOn("jandex")));

    }

}
