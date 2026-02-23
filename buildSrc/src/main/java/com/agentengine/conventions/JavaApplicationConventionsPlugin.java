package com.agentengine.conventions;

import java.util.List;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.tasks.compile.JavaCompile;

public class JavaApplicationConventionsPlugin extends BaseJavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        // Apply base plugins
        project.getPluginManager().apply(ApplicationPlugin.class);

        configureCommonFunctionality(project);

        project.getConfigurations().configureEach(configuration -> {
            configuration.exclude(Map.of("group", "org.springframework.boot", "module", "spring-boot-starter-logging"));
            configuration.exclude(Map.of("group", "ch.qos.logback", "module", "logback-classic"));
            configuration.exclude(Map.of("group", "ch.qos.logback", "module", "logback-core"));
        });
    }
}