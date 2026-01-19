package com.agentengine.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ApplicationPlugin;

public class JavaApplicationConventionsPlugin extends BaseJavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        // Apply base plugins
        project.getPluginManager().apply(ApplicationPlugin.class);

        configureCommonFunctionality(project);
    }
}