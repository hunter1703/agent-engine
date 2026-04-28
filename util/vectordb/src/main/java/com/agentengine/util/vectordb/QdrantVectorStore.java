package com.agentengine.util.vectordb;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.VectorsFactory.namedVectors;
import static io.qdrant.client.grpc.Points.*;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QdrantVectorStore<T extends VectorEntity> extends VectorStore<T> {

    private static final Logger LOG = LoggerFactory.getLogger(QdrantVectorStore.class);

    private static final int DEFAULT_MAX_RESULTS = 10;

    private final String collection;
    private final QdrantClient client;

    protected QdrantVectorStore(
            final String collection,
            final VectorDbClientFactory clientFactory,
            final BiFunction<String, String, float[]> embeddingGenerator) {
        super(embeddingGenerator);
        this.collection = collection;
        this.client = clientFactory.getClient();
    }

    /** Serializes {@code entity} into a Qdrant payload map. */
    protected abstract Map<String, Value> toPayload(T entity);

    /** Deserializes a Qdrant payload map into a domain entity. */
    protected abstract T fromPayload(Map<String, Value> payload);

    // ── VectorStore-specific ──────────────────────────────────────────────────

    @Override
    public long deleteByQuery(final Query query) {
        try {
            client.deleteAsync(collection, toQdrantFilter(query.getFilter())).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during delete by filter", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant delete by filter failed", e.getCause());
        }
        return 0L;
    }

    @Override
    protected PaginatedResult<T> findBySemanticQueryInternal(final Query query) {
        try {
            Page page = query.getPage();
            page = page == null ? new Page(0, DEFAULT_MAX_RESULTS) : page;
            final int maxResults = page.getLimit();
            final float[] queryVector = extractQueryVector(query.getFilter());
            final String vectorField = extractVectorField(query.getFilter());
            final double minScore = extractMinScore(query.getFilter());
            final Points.Filter qdrantFilter = buildQdrantFilter(query.getFilter());

            final SearchPoints.Builder request = SearchPoints.newBuilder()
                    .setCollectionName(collection)
                    .addAllVector(toFloatList(queryVector))
                    .setLimit(maxResults)
                    .setScoreThreshold((float) minScore)
                    .setWithPayload(withPayload());

            if (StringUtils.isNotBlank(vectorField)) {
                request.setVectorName(vectorField);
            }
            if (qdrantFilter != null) {
                request.setFilter(qdrantFilter);
            }

            final List<ScoredPoint> hits = client.searchAsync(request.build()).get();
            return PaginatedResult.create(
                    hits.stream().map(p -> fromPayload(p.getPayloadMap())).toList(), page, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during search", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant search failed", e.getCause());
        }
    }

    // ── Repository<T> ─────────────────────────────────────────────────────────

    @Override
    public T save(final T entity) {
        try {
            final Map<String, Value> payload = toPayload(entity);
            final Map<String, Vector> namedVecs = buildNamedVectors(entity);
            final PointStruct point = PointStruct.newBuilder()
                    .setId(toPointId(entity.getId()))
                    .setVectors(namedVectors(namedVecs))
                    .putAllPayload(payload)
                    .build();
            client.upsertAsync(collection, List.of(point)).get();
            return entity;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during upsert", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant upsert failed for entity " + entity.getId(), e.getCause());
        }
    }

    @Override
    public boolean deleteById(final String id) {
        try {
            client.deleteAsync(collection, List.of(toPointId(id))).get();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during delete", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant delete failed for id " + id, e.getCause());
        }
    }

    // ── Unsupported ReadRepository operations ─────────────────────────────────

    @Override
    public T insert(final T entity) {
        return save(entity);
    }

    @Override
    public T update(final String id, final T entity) {
        return save(entity);
    }

    @Override
    public T update(final String id, final Update update) {
        throw new UnsupportedOperationException("Partial updates are not supported on the vector store");
    }

    @Override
    public T findById(final String id) {
        return findById(id, null, null);
    }

    @Override
    public T findById(final String id, final List<String> includeFields, final List<String> excludeFields) {
        final Map<String, T> result = retrievePoints(List.of(id), includeFields, excludeFields);
        return result.get(id);
    }

    @Override
    public Map<String, T> findByIds(final Collection<String> ids) {
        return retrievePoints(ids, null, null);
    }

    @Override
    public Map<String, T> findByIds(
            final Collection<String> ids, final List<String> includeFields, final List<String> excludeFields) {
        return retrievePoints(ids, includeFields, excludeFields);
    }

    private Map<String, T> retrievePoints(
            final Collection<String> ids, final List<String> includeFields, final List<String> excludeFields) {
        final List<PointId> pointIds =
                ids.stream().map(QdrantVectorStore::toPointId).toList();
        final WithPayloadSelector payloadSelector = buildPayloadSelector(includeFields, excludeFields);
        try {
            final List<RetrievedPoint> points = client.retrieveAsync(collection, pointIds, payloadSelector, null, null)
                    .get();
            final Map<String, T> result = new LinkedHashMap<>();
            for (final RetrievedPoint point : points) {
                final T entity = fromPayload(point.getPayloadMap());
                result.put(entity.getId(), entity);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retrieve", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Qdrant retrieve failed for ids " + ids, e.getCause());
        }
    }

    private static WithPayloadSelector buildPayloadSelector(
            final List<String> includeFields, final List<String> excludeFields) {
        if (includeFields != null && !includeFields.isEmpty()) {
            return WithPayloadSelectorFactory.include(includeFields);
        }
        if (excludeFields != null && !excludeFields.isEmpty()) {
            return WithPayloadSelectorFactory.exclude(excludeFields);
        }
        return withPayload();
    }

    @Override
    public long count() {
        throw new UnsupportedOperationException("Count is not supported on the vector store");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PointId toPointId(final String id) {
        return PointId.newBuilder()
                .setUuid(UUID.nameUUIDFromBytes(id.getBytes()).toString())
                .build();
    }

    private static Map<String, Vector> buildNamedVectors(final VectorEntity entity) {
        final Map<String, float[]> vectors = entity.getVectors();
        final Map<String, Vector> result = new HashMap<>();
        vectors.forEach((field, vec) -> result.put(field, toVector(vec)));
        return result;
    }

    /**
     * Translates a {@link Filter} to a Qdrant filter for delete operations.
     * Supports EQ (keyword match) and AND compound filters.
     */
    private static io.qdrant.client.grpc.Points.Filter toQdrantFilter(final Filter filter) {
        final io.qdrant.client.grpc.Points.Filter.Builder builder = io.qdrant.client.grpc.Points.Filter.newBuilder();

        if (filter.getOp() == Operator.AND && filter.getValues() != null) {
            for (final Object child : filter.getValues()) {
                if (child instanceof final Filter childFilter) {
                    builder.addMust(io.qdrant.client.ConditionFactory.filter(toQdrantFilter(childFilter)));
                }
            }
        } else if (filter.getOp() == Operator.EQ
                && filter.getField() != null
                && filter.getValues() != null
                && !filter.getValues().isEmpty()) {
            builder.addMust(matchKeyword(
                    filter.getField(), String.valueOf(filter.getValues().getFirst())));
        }

        return builder.build();
    }

    private static Points.Filter buildQdrantFilter(final Filter filter) {
        if (filter == null) {
            return null;
        }

        final Operator op = filter.getOp();
        if (op == Operator.SEMANTIC_SEARCH) {
            return null;
        }

        final List<Object> values = filter.getValues();
        if (op == Operator.EQ) {
            return Points.Filter.newBuilder()
                    .addMust(matchKeyword(filter.getField(), String.valueOf(values.getFirst())))
                    .build();
        }

        if (op.isCompound() && CollectionUtils.isNotEmpty(values)) {
            final Points.Filter.Builder builder = Points.Filter.newBuilder();
            for (final Object child : values) {
                final Filter childFilter = (Filter) child;
                final Points.Filter sub = buildQdrantFilter(childFilter);
                if (sub != null) {
                    if (op == Operator.AND) {
                        builder.addMust(ConditionFactory.filter(sub));
                    } else {
                        builder.addShould(ConditionFactory.filter(sub));
                    }
                }
            }
            return builder.getMustCount() > 0 || builder.getShouldCount() > 0 ? builder.build() : null;
        }

        return null;
    }

    private static String extractVectorField(final Filter filter) {
        final Filter semantic = findSemanticFilter(filter);
        return semantic == null ? null : semantic.getField();
    }

    private static float[] extractQueryVector(final Filter filter) {
        final Filter semantic = findSemanticFilter(filter);
        if (semantic == null
                || semantic.getValues() == null
                || semantic.getValues().isEmpty()) {
            throw new IllegalArgumentException("SEMANTIC_SEARCH filter must have a queryVector value");
        }
        return (float[]) semantic.getValues().getFirst();
    }

    private static Filter findSemanticFilter(final Filter filter) {
        if (filter == null) return null;
        if (filter.getOp() == Operator.SEMANTIC_SEARCH) return filter;
        if (filter.getOp() == Operator.AND && filter.getValues() != null) {
            return filter.getValues().stream()
                    .filter(Filter.class::isInstance)
                    .map(Filter.class::cast)
                    .filter(f -> f.getOp() == Operator.SEMANTIC_SEARCH)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static double extractMinScore(final Filter filter) {
        final Filter semantic = findSemanticFilter(filter);
        if (semantic == null || semantic.getAdditional() == null) return 0.0;
        final Object val = semantic.getAdditional().get("minScore");
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static Vector toVector(final float[] arr) {
        return Vector.newBuilder().addAllData(toFloatList(arr)).build();
    }

    private static List<Float> toFloatList(final float[] arr) {
        final List<Float> list = new ArrayList<>(arr.length);
        for (final float f : arr) {
            list.add(f);
        }
        return list;
    }

    private static WithPayloadSelector withPayload() {
        return WithPayloadSelector.newBuilder().setEnable(true).build();
    }

    protected static String strValue(final Map<String, Value> payload, final String key) {
        final Value value = payload.get(key);
        return value != null ? value.getStringValue() : "";
    }

    protected static int intVal(final Map<String, Value> payload, final String key) {
        final Value value = payload.get(key);
        return value != null ? (int) value.getIntegerValue() : 0;
    }
}
