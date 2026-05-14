package com.agentengine.util.vectordb;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import java.util.*;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QdrantVectorStore<T extends VectorEntity> extends VectorStore<T> {

    private static final Logger LOG = LoggerFactory.getLogger(QdrantVectorStore.class);

    private static final int DEFAULT_MAX_RESULTS = 10;

    private final String collection;
    private final VectorDbClientFactory clientFactory;

    protected QdrantVectorStore(
            final String collection,
            final VectorDbClientFactory clientFactory,
            final BiFunction<String, String, float[]> embeddingGenerator) {
        super(embeddingGenerator);
        this.collection = collection;
        this.clientFactory = clientFactory;
    }

    private QdrantHttpClient client() {
        return clientFactory.getClient();
    }

    /** Serializes {@code entity} into a Qdrant payload map. */
    protected abstract Map<String, Object> toPayload(T entity);

    /** Deserializes a Qdrant payload map into a domain entity. */
    protected abstract T fromPayload(Map<String, Object> payload);

    // ── VectorStore-specific ──────────────────────────────────────────────────

    @Override
    public long deleteByQuery(final Query query) {
        LOG.info("QdrantVectorStore.deleteByQuery: collection={} filter={}", collection, query.getFilter());
        final QdrantHttpClient.Filter filter = toQdrantFilter(query.getFilter());
        LOG.info("QdrantVectorStore.deleteByQuery: translated filter={}", filter);
        final QdrantHttpClient.DeleteRequest request = new QdrantHttpClient.DeleteRequest(null, filter);
        client().delete(collection, request);
        return 0L;
    }

    @Override
    protected PaginatedResult<T> findBySemanticQueryInternal(final Query query) {
        Page page = query.getPage();
        page = page == null ? new Page(0, DEFAULT_MAX_RESULTS) : page;
        final int maxResults = page.getLimit();
        final float[] queryVector = extractQueryVector(query.getFilter());
        final String vectorField = extractVectorField(query.getFilter());
        final double minScore = extractMinScore(query.getFilter());
        final QdrantHttpClient.Filter qdrantFilter = buildQdrantFilter(query.getFilter());

        final QdrantHttpClient.SearchRequest request = new QdrantHttpClient.SearchRequest(
                toFloatList(queryVector),
                StringUtils.isNotBlank(vectorField) ? vectorField : null,
                maxResults,
                (float) minScore,
                qdrantFilter,
                true);

        final QdrantHttpClient.SearchResponse response = client().search(collection, request);
        final List<T> results =
                response.result().stream().map(p -> fromPayload(p.payload())).toList();
        return PaginatedResult.create(results, page, null);
    }

    // ── Repository<T> ─────────────────────────────────────────────────────────

    @Override
    public T save(final T entity) {
        final Map<String, Object> payload = toPayload(entity);
        final Object vectorData = buildVectorData(entity);
        final QdrantHttpClient.Point point = new QdrantHttpClient.Point(entity.getId(), vectorData, payload);
        final QdrantHttpClient.UpsertRequest request = new QdrantHttpClient.UpsertRequest(List.of(point));
        client().upsert(collection, request);
        return entity;
    }

    @Override
    public boolean deleteById(final String id) {
        final QdrantHttpClient.DeleteRequest request = new QdrantHttpClient.DeleteRequest(List.of(id), null);
        client().delete(collection, request);
        return true;
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
        // Note: HTTP API doesn't support field projection like gRPC, so we ignore includeFields/excludeFields
        final QdrantHttpClient.RetrieveRequest request =
                new QdrantHttpClient.RetrieveRequest(new ArrayList<>(ids), true);
        final QdrantHttpClient.RetrieveResponse response = client().retrieve(collection, request);
        final Map<String, T> result = new LinkedHashMap<>();
        for (final QdrantHttpClient.RetrievedPoint point : response.result()) {
            final T entity = fromPayload(point.payload());
            result.put(entity.getId(), entity);
        }
        return result;
    }

    @Override
    public long count() {
        throw new UnsupportedOperationException("Count is not supported on the vector store");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds vector data for Qdrant upsert.
     * If entity has a single vector, returns a flat List<Float>.
     * If entity has multiple named vectors, returns a Map<String, List<Float>>.
     */
    private static Object buildVectorData(final VectorEntity entity) {
        final Map<String, float[]> vectors = entity.getVectors();
        if (vectors.size() == 1) {
            // Single unnamed vector: return flat array
            final float[] vec = vectors.values().iterator().next();
            return toFloatList(vec);
        } else {
            // Multiple named vectors: return map
            return buildNamedVectors(entity);
        }
    }

    private static Map<String, List<Float>> buildNamedVectors(final VectorEntity entity) {
        final Map<String, float[]> vectors = entity.getVectors();
        final Map<String, List<Float>> result = new HashMap<>();
        vectors.forEach((field, vec) -> result.put(field, toFloatList(vec)));
        return result;
    }

    /**
     * Translates a {@link Filter} to a Qdrant filter for delete operations.
     * Supports EQ (keyword match) and AND compound filters.
     */
    private static QdrantHttpClient.Filter toQdrantFilter(final Filter filter) {
        LOG.debug(
                "toQdrantFilter: input filter op={} field={} values={}",
                filter.getOp(),
                filter.getField(),
                filter.getValues());

        final List<QdrantHttpClient.Condition> must = new ArrayList<>();

        if (filter.getOp() == Operator.AND && filter.getValues() != null) {
            LOG.debug(
                    "toQdrantFilter: processing AND with {} children",
                    filter.getValues().size());
            for (final Object child : filter.getValues()) {
                if (child instanceof final Filter childFilter) {
                    final QdrantHttpClient.Filter subFilter = toQdrantFilter(childFilter);
                    if (subFilter != null && subFilter.must() != null) {
                        LOG.debug(
                                "toQdrantFilter: adding {} conditions from child",
                                subFilter.must().size());
                        must.addAll(subFilter.must());
                    }
                }
            }
        } else if (filter.getOp() == Operator.EQ
                && filter.getField() != null
                && filter.getValues() != null
                && !filter.getValues().isEmpty()) {
            final String value = String.valueOf(filter.getValues().getFirst());
            LOG.debug("toQdrantFilter: creating EQ condition field={} value={}", filter.getField(), value);
            final QdrantHttpClient.MatchCondition matchCondition = new QdrantHttpClient.MatchCondition(value);
            must.add(new QdrantHttpClient.Condition(filter.getField(), matchCondition));
        }

        final QdrantHttpClient.Filter result = must.isEmpty() ? null : new QdrantHttpClient.Filter(must, null);
        LOG.info("toQdrantFilter: result filter with {} conditions", must.size());
        return result;
    }

    private static QdrantHttpClient.Filter buildQdrantFilter(final Filter filter) {
        if (filter == null) {
            return null;
        }

        final Operator op = filter.getOp();
        if (op == Operator.SEMANTIC_SEARCH) {
            return null;
        }

        final List<Object> values = filter.getValues();
        if (op == Operator.EQ) {
            final String value = String.valueOf(values.getFirst());
            final QdrantHttpClient.MatchCondition matchCondition = new QdrantHttpClient.MatchCondition(value);
            return new QdrantHttpClient.Filter(
                    List.of(new QdrantHttpClient.Condition(filter.getField(), matchCondition)), null);
        }

        if (op.isCompound() && CollectionUtils.isNotEmpty(values)) {
            final List<QdrantHttpClient.Condition> must = new ArrayList<>();
            final List<QdrantHttpClient.Condition> should = new ArrayList<>();
            for (final Object child : values) {
                final Filter childFilter = (Filter) child;
                final QdrantHttpClient.Filter sub = buildQdrantFilter(childFilter);
                if (sub != null) {
                    if (op == Operator.AND) {
                        if (sub.must() != null) must.addAll(sub.must());
                    } else {
                        if (sub.must() != null) should.addAll(sub.must());
                    }
                }
            }
            if (!must.isEmpty() || !should.isEmpty()) {
                return new QdrantHttpClient.Filter(must.isEmpty() ? null : must, should.isEmpty() ? null : should);
            }
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

    private static List<Float> toFloatList(final float[] arr) {
        final List<Float> list = new ArrayList<>(arr.length);
        for (final float f : arr) {
            list.add(f);
        }
        return list;
    }

    protected static String strValue(final Map<String, Object> payload, final String key) {
        final Object value = payload.get(key);
        return value != null ? value.toString() : "";
    }

    protected static int intVal(final Map<String, Object> payload, final String key) {
        final Object value = payload.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
