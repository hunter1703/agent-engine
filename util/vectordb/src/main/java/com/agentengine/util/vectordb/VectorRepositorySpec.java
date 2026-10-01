package com.agentengine.util.vectordb;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * What a repository registers with a {@link VectorBackend}: its entity class's collection, kept for
 * each customer as {@code name} in the {@code clientType} store. The entity's own fields are mapped
 * to and from the stored payload by {@code toPayload} and {@code fromPayload}, besides the base
 * fields every stored entity carries; {@code queryEmbedder} turns a semantic query's text into a
 * vector with the given embedding model.
 */
public record VectorRepositorySpec<T extends VectorEntity>(
    String name,
    Class<T> entityClass,
    VectorStoreClientType clientType,
    Function<T, Map<String, Object>> toPayload,
    Function<Map<String, Object>, T> fromPayload,
    BiFunction<String, String, float[]> queryEmbedder) {}
