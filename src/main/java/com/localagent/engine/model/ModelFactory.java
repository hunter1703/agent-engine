package com.localagent.engine.model;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public final class ModelFactory {
    private ModelFactory() {
    }

    public static LLMModel buildChatModel(String provider, String modelName, Map<String, Object> modelConfig) {
        String normalized = normalizeProvider(provider);
        ChatModel model;
        switch (normalized) {
            case "ollama" -> model = buildOllama(modelName, modelConfig);
            case "llamacpp" -> model = buildLlamaCpp(modelName, modelConfig);
            default -> throw new IllegalArgumentException("Unsupported provider: " + normalized);
        }
        return new LangChain4JLLMModel(model);
    }

    public static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "ollama";
        }
        String value = provider.trim().toLowerCase(Locale.ROOT);
        if (value.equals("llama.cpp") || value.equals("llama_cpp") || value.equals("llamacpp")) {
            return "llamacpp";
        }
        return value;
    }

    private static ChatLanguageModel buildOpenAi(String modelName, Map<String, Object> config) {
        OpenAiChatModel.Builder builder = OpenAiChatModel.builder();
        builder.modelName(modelName);
        String apiKey = asString(config.get("api_key"));
        if (apiKey != null) {
            builder.apiKey(apiKey);
        }
        String baseUrl = asString(config.get("base_url"));
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        applyDouble(builder, "temperature", config.get("temperature"));
        applyDouble(builder, "topP", config.get("top_p"));
        return builder.build();
    }

    private static ChatModel buildOllama(String modelName, Map<String, Object> config) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder();
        builder.modelName(modelName);
        String baseUrl = asString(config.get("base_url"));
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        applyDouble(builder, "temperature", config.get("temperature"));
        applyDouble(builder, "topK", config.get("top_k"));
        applyDouble(builder, "topP", config.get("top_p"));
        applyDouble(builder, "repeatPenalty", config.get("repeat_penalty"));
        applyInteger(builder, "numPredict", config.get("num_predict"));
        applyInteger(builder, "numCtx", config.get("num_ctx"));
        applyStopTokens(builder, config.get("stop"));
        return builder.build();
    }

    private static ChatLanguageModel buildLlamaCpp(String modelName, Map<String, Object> config) {
        Object builder = buildLlamaCppBuilder();
        invokeIfPresent(builder, "modelName", String.class, modelName);
        String baseUrl = asString(config.get("base_url"));
        if (baseUrl != null) {
            invokeIfPresent(builder, "baseUrl", String.class, baseUrl);
        }
        applyDouble(builder, "temperature", config.get("temperature"));
        applyDouble(builder, "topK", config.get("top_k"));
        applyDouble(builder, "topP", config.get("top_p"));
        applyDouble(builder, "repeatPenalty", config.get("repeat_penalty"));
        applyInteger(builder, "numPredict", config.get("num_predict"));
        applyInteger(builder, "numCtx", config.get("num_ctx"));
        applyStopTokens(builder, config.get("stop"));
        return buildLlamaCppModel(builder);
    }

    private static Object buildLlamaCppBuilder() {
        try {
            Class<?> modelClass = Class.forName("dev.langchain4j.model.llamacpp.LlamaCppChatModel");
            return modelClass.getMethod("builder").invoke(null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Llama.cpp support is not on the classpath; add the langchain4j-llama-cpp module.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Llama.cpp model builder.", e);
        }
    }

    private static ChatLanguageModel buildLlamaCppModel(Object builder) {
        try {
            Object model = builder.getClass().getMethod("build").invoke(builder);
            return (ChatLanguageModel) model;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Llama.cpp model.", e);
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void applyDouble(Object builder, String methodName, Object value) {
        Double parsed = asDouble(value);
        if (parsed == null) {
            return;
        }
        invokeIfPresent(builder, methodName, Double.class, parsed);
    }

    private static void applyInteger(Object builder, String methodName, Object value) {
        Integer parsed = asInteger(value);
        if (parsed == null) {
            return;
        }
        invokeIfPresent(builder, methodName, Integer.class, parsed);
    }

    private static void applyStopTokens(Object builder, Object value) {
        List<String> tokens = asStringList(value);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        invokeIfPresent(builder, "stop", List.class, tokens);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return null;
    }

    private static void invokeIfPresent(Object target, String methodName, Class<?> paramType, Object value) {
        try {
            Method method = target.getClass().getMethod(methodName, paramType);
            method.invoke(target, value);
        } catch (Exception ignored) {
            // Ignore missing builder methods for unsupported options
        }
    }
}
