package com.agentengine.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.jetbrains.annotations.NotNull;

public class JavaLibraryConventionsPlugin extends BaseJavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(final @NotNull Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply("maven-publish");
        configureCommonFunctionality(project);

        project.getExtensions().configure(org.gradle.api.publish.PublishingExtension.class, publishing -> {
            publishing.getPublications().create("mavenJava", org.gradle.api.publish.maven.MavenPublication.class, mavenPublication -> {
                mavenPublication.from(project.getComponents().getByName("java"));
            });
            publishing.getRepositories().maven(mavenArtifactRepository -> {
                mavenArtifactRepository.setName("GitHubPackages");
                mavenArtifactRepository.setUrl(project.uri("https://maven.pkg.github.com/hunter1703/agent-engine"));
                mavenArtifactRepository.credentials(credentials -> {
                    credentials.setUsername((String) (project.hasProperty("gpr.user") ? project.property("gpr.user") : System.getenv("GITHUB_ACTOR")));
                    credentials.setPassword((String) (project.hasProperty("gpr.key") ? project.property("gpr.key") : System.getenv("GITHUB_TOKEN")));
                });
            });
        });
    }
}
