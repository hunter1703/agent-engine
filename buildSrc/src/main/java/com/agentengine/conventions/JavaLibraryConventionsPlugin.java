package com.agentengine.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.jetbrains.annotations.NotNull;

public class JavaLibraryConventionsPlugin extends BaseJavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(final @NotNull Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        configureCommonFunctionality(project);
    }
}
