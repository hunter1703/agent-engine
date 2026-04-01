package com.agentengine.util.mongodb.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

class MongoClientFactoryTest {
    @Test
    void shouldResolveConnectionStringWhenConfigured() {
        final Config config = buildConfig(Map.of("mongodb.connection.string", "mongodb://example:27017"));

        assertThat(MongoClientFactory.resolveConnectionString(config)).isEqualTo("mongodb://example:27017");
    }

    @Test
    void shouldUseDefaultConnectionStringWhenConfigMissing() {
        final Config config = buildConfig(Map.of());

        assertThat(MongoClientFactory.resolveConnectionString(config)).isEqualTo("mongodb://localhost:27018");
    }

    private static Config buildConfig(final Map<String, String> values) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(new MapConfigSource(values))
                .build();
    }

    private record MapConfigSource(Map<String, String> values) implements ConfigSource {
        @Override
        public Map<String, String> getProperties() {
            return values;
        }

        @Override
        public String getValue(final String propertyName) {
            return values.get(propertyName);
        }

        @Override
        public Set<String> getPropertyNames() {
            return values.keySet();
        }

        @Override
        public String getName() {
            return "test-map-config-source";
        }
    }
}
