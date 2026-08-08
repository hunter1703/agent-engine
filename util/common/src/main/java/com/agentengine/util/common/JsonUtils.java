package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonLiteral;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

    private static final SimpleModule KOTLINX_JSON_MODULE = new SimpleModule()
            .addSerializer(JsonElement.class, new StdSerializer<>(JsonElement.class) {
                @Override
                public void serialize(JsonElement value, JsonGenerator gen, SerializerProvider provider)
                        throws IOException {
                    gen.writeRawValue(value.toString());
                }
            });

    private static final JsonMapper JSON_MAPPER = createJsonMapper(false);
    private static final JsonMapper JSON_MAPPER_WITH_TYPE = createJsonMapper(true);

    private JsonUtils() {}

    private static JsonMapper createJsonMapper(boolean includeTypeInfo) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .addModule(new Jdk8Module())
                .addModule(new JavaTimeModule())
                .addModule(new GuavaModule())
                .addModule(KOTLINX_JSON_MODULE)
                .serializationInclusion(JsonInclude.Include.NON_ABSENT);

        if (includeTypeInfo) {
            BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType(Object.class)
                    .build();
            builder.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        }

        return builder.build();
    }

    /**
     * An independent copy of the default mapper
     */
    public static ObjectMapper copyMapper() {
        return JSON_MAPPER.copy();
    }

    public static <T> T fromMap(final Map<String, Object> map, final Class<T> clazz) {
        if (map == null) {
            return null;
        }
        return JSON_MAPPER.convertValue(map, clazz);
    }

    public static <T> T fromMap(final Map<String, Object> map, final TypeReference<T> typeReference) {
        if (map == null) {
            return null;
        }
        return JSON_MAPPER.convertValue(map, typeReference);
    }

    public static <T> T fromJson(final String json, final Class<T> clazz) {
        return fromJson(json, clazz, false);
    }

    public static <T> T fromJson(final String json, final Class<T> clazz, boolean includesTypeInfo) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper(includesTypeInfo).readValue(json, clazz);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static <T> T fromJson(final String json, final Type type) {
        return fromJson(json, type, false);
    }

    public static <T> T fromJson(final String json, final Type type, boolean includesTypeInfo) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            ObjectMapper mapper = mapper(includesTypeInfo);
            return mapper.readValue(json, mapper.getTypeFactory().constructType(type));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static <T> T fromJson(final String json, final TypeReference<T> typeReference) {
        return fromJson(json, typeReference, false);
    }

    public static <T> T fromJson(final String json, final TypeReference<T> typeReference, boolean includesTypeInfo) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper(includesTypeInfo).readValue(json, typeReference);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz) {
        return fromStream(inputStream, clazz, false);
    }

    public static <T> T fromStream(final InputStream inputStream, final Class<T> clazz, boolean includesTypeInfo) {
        if (inputStream == null) {
            return null;
        }
        try {
            return mapper(includesTypeInfo).readValue(inputStream, clazz);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static <T> T fromFile(final Path path, final Class<T> clazz) {
        return fromFile(path, clazz, false);
    }

    public static <T> T fromFile(final Path path, final Class<T> clazz, boolean includesTypeInfo) {
        try (InputStream stream = Files.newInputStream(path)) {
            return mapper(includesTypeInfo).readValue(stream, clazz);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T fromFile(final Path path, final TypeReference<T> typeReference) {
        return fromFile(path, typeReference, false);
    }

    public static <T> T fromFile(final Path path, final TypeReference<T> typeReference, boolean includesTypeInfo) {
        try (InputStream stream = Files.newInputStream(path)) {
            return mapper(includesTypeInfo).readValue(stream, typeReference);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String toJson(final Object value) {
        return toJson(value, false);
    }

    public static String toJson(final Object value, boolean includeTypeInfo) {
        if (value == null) {
            return null;
        }
        try {
            return getObjectWriter(includeTypeInfo).writeValueAsString(value);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void toStream(final OutputStream os, final Object value) throws IOException {
        JSON_MAPPER.writeValue(os, value);
    }

    public static Map<String, Object> toMap(final Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON_MAPPER.convertValue(obj, new TypeReference<>() {});
    }

    public static String toStableJson(final Object value) {
        return toJson(value);
    }

    public static void removeValue(final Object jsonObject, final String path) {
        JsonPath.using(Configuration.builder().build()).parse(jsonObject).delete(path);
    }

    public static Map<String, Object> parseJsonPayload(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            final int end = cleaned.lastIndexOf("```");
            if (end > 2) {
                cleaned = cleaned.substring(3, end).trim();
                if (cleaned.startsWith("json")) {
                    cleaned = cleaned.substring(4).trim();
                }
            }
        }
        Map<String, Object> payload = null;
        try {
            payload = fromJson(cleaned, new TypeReference<>() {});
        } catch (Exception ex) {
            final int start = cleaned.indexOf('{');
            final int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    payload = fromJson(cleaned.substring(start, end + 1), new TypeReference<>() {});
                } catch (Exception innerEx) {
                    LOGGER.warn("Failed to parse JSON payload from substring", innerEx);
                }
            }
        }
        return payload;
    }

    public static JsonNode toJsonNode(final Object object) {
        if (object == null) {
            return null;
        }

        return mapper(false).valueToTree(object);
    }

    public static JsonNode toJsonNode(final String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }

        try {
            return mapper(false).readTree(json);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    // --- kotlinx JsonElement helpers ---

    public static JsonPrimitive strVal(final String value) {
        return new JsonLiteral(value, true, null);
    }

    public static JsonElement nullableVal(final String value) {
        return value != null ? strVal(value) : JsonNull.INSTANCE;
    }

    public static JsonElement boolVal(final boolean value) {
        return new JsonLiteral(value, false, null);
    }

    public static JsonElement numVal(final long value) {
        return new JsonLiteral(value, false, null);
    }

    private static ObjectWriter getObjectWriter(final boolean includeTypeInfo) {
        JsonMapper mapper = mapper(includeTypeInfo);
        return includeTypeInfo ? mapper.writerFor(Object.class) : mapper.writer();
    }

    private static JsonMapper mapper(boolean includeTypeInfo) {
        return includeTypeInfo ? JSON_MAPPER_WITH_TYPE : JSON_MAPPER;
    }
}
