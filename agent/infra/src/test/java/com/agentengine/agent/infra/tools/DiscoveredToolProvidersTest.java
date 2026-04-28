package com.agentengine.agent.infra.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.agent.infra.tools.DiscoveredToolProviders.ToolParameter;
import com.agentengine.agent.infra.tools.file.ReadFileTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import java.lang.reflect.Constructor;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscoveredToolProvidersTest {

    @Test
    void shouldPopulateConfigsSchemaFromConstructorAnnotationsWhenToolIsDiscoverable() throws NoSuchMethodException {
        final Constructor<EchoTool> constructor = EchoTool.class.getDeclaredConstructor(String.class);
        final java.util.List<ToolParameter> params = DiscoveredToolProviders.resolveParameters(constructor);

        final ToolDescriptor descriptor = DiscoveredToolProviders.getDescriptor(EchoTool.class, params);

        assertThat(descriptor.name()).isEqualTo("echo");
        assertThat(descriptor.configsSchema()).containsEntry("type", "object");
        assertThat(descriptor.configsSchema()).doesNotContainKey("required");

        @SuppressWarnings("unchecked")
        final Map<String, Object> properties =
                (Map<String, Object>) descriptor.configsSchema().get("properties");
        @SuppressWarnings("unchecked")
        final Map<String, Object> prefix = (Map<String, Object>) properties.get("prefix");

        assertThat(prefix).containsEntry("type", "string");
        assertThat(prefix.get("description").toString()).contains("Prefix to add to the echoed message");
        assertThat(prefix.get("description").toString()).contains("Optional.");
    }

    @Test
    void shouldKeepExistingConfigsSchemaWhenDiscoverableToolConstructorHasNoParameters() throws NoSuchMethodException {
        final Constructor<ReadFileTool> constructor = ReadFileTool.class.getDeclaredConstructor();
        final java.util.List<ToolParameter> params = DiscoveredToolProviders.resolveParameters(constructor);

        final ToolDescriptor descriptor = DiscoveredToolProviders.getDescriptor(ReadFileTool.class, params);

        assertThat(descriptor.name()).isEqualTo("read_file");
        assertThat(descriptor.configsSchema()).isEmpty();
    }
}
