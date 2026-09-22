package com.agentengine.util.vectordb;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.context.Context;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.VectorFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QdrantVectorStore<T extends VectorEntity> extends VectorStore<T> {

  private static final Logger LOG = LoggerFactory.getLogger(QdrantVectorStore.class);

  private final String collection;
  private final VectorDbClientFactory clientFactory;

  protected QdrantVectorStore(
      final String collection,
      final Class<T> entityClass,
      final VectorStoreClientType clientType,
      final VectorDbClientFactory clientFactory,
      final BiFunction<String, String, float[]> embeddingGenerator) {
    super(entityClass, clientType, embeddingGenerator);
    this.collection = collection;
    this.clientFactory = clientFactory;
  }

  @Override
  public long deleteByQuery(final Query query) {
    final Common.Filter filter = toDeleteFilter(query.getFilter());
    LOG.debug(
        "QdrantVectorStore.deleteByQuery: collection={} filter={} translatedFilter={}",
        collectionName(),
        query.getFilter(),
        filter);
    if (filter == null) {
      throw new IllegalArgumentException("Delete query has no supported filter");
    }
    await(client().deleteAsync(collectionName(), filter));
    return 0L;
  }

  @Override
  protected PaginatedResult<T> findBySemanticQueryInternal(final Query query) {
    final Page page = query.getPage();
    final int maxResults = page.getLimit() < 0 ? Integer.MAX_VALUE : page.getLimit();
    final Common.Filter qdrantFilter = buildQdrantFilter(query.getFilter());

    final List<Filter> semanticFilters = extractSemanticFilters(query.getFilter());
    if (semanticFilters.isEmpty()) {
      return PaginatedResult.create(List.of(), page, null);
    }

    final Points.QueryPoints.Builder request =
        Points.QueryPoints.newBuilder()
            .setCollectionName(collectionName())
            .setLimit(maxResults)
            .setWithPayload(WithPayloadSelectorFactory.enable(true));
    if (qdrantFilter != null) {
      request.setFilter(qdrantFilter);
    }

    // A single vector is a direct query. Several vectors are each wrapped in a 'prefetch'
    // sub-query, which Qdrant runs in parallel and merges via RRF.
    if (semanticFilters.size() == 1) {
      final Filter semantic = semanticFilters.getFirst();
      final float[] queryVector = (float[]) semantic.getValues().getFirst();
      final String vectorField = semantic.getField();

      request.setQuery(QueryFactory.nearest(queryVector));
      if (StringUtils.isNotBlank(vectorField)) {
        request.setUsing(vectorField);
      }
      request.setScoreThreshold((float) extractMinScore(semantic));
    } else {
      for (final Filter semantic : semanticFilters) {
        final Points.PrefetchQuery.Builder prefetch =
            Points.PrefetchQuery.newBuilder()
                .setQuery(QueryFactory.nearest((float[]) semantic.getValues().getFirst()))
                .setLimit(maxResults);
        if (StringUtils.isNotBlank(semantic.getField())) {
          prefetch.setUsing(semantic.getField());
        }
        request.addPrefetch(prefetch);
      }
      request.setQuery(QueryFactory.fusion(Points.Fusion.RRF));
    }

    final List<Points.ScoredPoint> points = await(client().queryAsync(request.build()));
    final List<T> results =
        points.stream()
            .map(point -> fromPayload(VectorDbUtils.fromValues(point.getPayloadMap())))
            .toList();
    return PaginatedResult.create(results, page, null);
  }

  @Override
  public T save(final T entity) {
    await(client().upsertAsync(collectionName(), List.of(toPoint(entity))));
    return entity;
  }

  @Override
  public List<T> insertMany(final List<T> entities) {
    if (CollectionUtils.isEmpty(entities)) {
      return List.of();
    }
    final List<Points.PointStruct> points = new ArrayList<>(entities.size());
    for (final T entity : entities) {
      points.add(toPoint(entity));
    }
    await(client().upsertAsync(collectionName(), points));
    return entities;
  }

  @Override
  public boolean deleteById(final String id) {
    await(client().deleteAsync(collectionName(), List.of(pointId(id))));
    return true;
  }

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
    throw new UnsupportedOperationException(
        "Partial updates are not supported on the vector store");
  }

  @Override
  public T findOneAndUpdate(final Query query, final Update update) {
    throw new UnsupportedOperationException(
        "Partial updates are not supported on the vector store");
  }

  @Override
  public long updateOne(final Filter filter, final Update update) {
    throw new UnsupportedOperationException(
        "Partial updates are not supported on the vector store");
  }

  @Override
  public long updateMany(final Filter filter, final Update update) {
    throw new UnsupportedOperationException(
        "Partial updates are not supported on the vector store");
  }

  @Override
  public T update(final String id, final Long expectedVersion, final T entity) {
    throw new UnsupportedOperationException(
        "Versioned updates are not supported on the vector store");
  }

  @Override
  public T findById(final String id) {
    return findById(id, null, null);
  }

  @Override
  public T findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    final Map<String, T> result = retrievePoints(List.of(id), includeFields, excludeFields);
    return result.get(id);
  }

  @Override
  public Map<String, T> findByIds(final Collection<String> ids) {
    return retrievePoints(ids, null, null);
  }

  @Override
  public Map<String, T> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    return retrievePoints(ids, includeFields, excludeFields);
  }

  protected abstract Map<String, Object> toPayload(T entity);

  protected abstract T fromPayload(Map<String, Object> payload);

  private Map<String, T> retrievePoints(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    final List<Common.PointId> pointIds = ids.stream().map(QdrantVectorStore::pointId).toList();
    final List<Points.RetrievedPoint> points =
        await(
            client()
                .retrieveAsync(
                    collectionName(),
                    pointIds,
                    payloadSelector(includeFields, excludeFields),
                    WithVectorsSelectorFactory.enable(false),
                    null));
    final Map<String, T> result = new LinkedHashMap<>();
    for (final Points.RetrievedPoint point : points) {
      final T entity = fromPayload(VectorDbUtils.fromValues(point.getPayloadMap()));
      result.put(entity.getId(), entity);
    }
    return result;
  }

  @Override
  protected void setup(final int vectorSize) {
    final QdrantClient client = client();
    final String name = collectionName();
    if (!await(client.collectionExistsAsync(name))) {
      final Map<String, Collections.VectorParams> vectors = new LinkedHashMap<>();
      for (final String vectorName : getFieldVsVectorName().values()) {
        vectors.put(
            vectorName,
            Collections.VectorParams.newBuilder()
                .setSize(vectorSize)
                .setDistance(Collections.Distance.Cosine)
                .build());
      }
      await(client.createCollectionAsync(name, vectors));
    }
    // Qdrant Cloud rejects a filter on an unindexed field; creating an existing index is a no-op.
    for (final String field : getIndexedFields()) {
      await(
          client.createPayloadIndexAsync(
              name, field, Collections.PayloadSchemaType.Keyword, null, true, null, null));
    }
  }

  private String collectionName() {
    return collection + "_" + Context.customerId().orElseThrow();
  }

  private QdrantClient client() {
    return clientFactory.getClient(clientType(), Context.customerId().orElseThrow());
  }

  /**
   * Qdrant takes either an include list or an exclude list, not both, so when both are given the
   * excluded fields are dropped from the include list.
   */
  private static Points.WithPayloadSelector payloadSelector(
      final List<String> includeFields, final List<String> excludeFields) {
    if (CollectionUtils.isNotEmpty(includeFields)) {
      final List<String> fields = new ArrayList<>(includeFields);
      fields.removeAll(CollectionUtils.nullSafeList(excludeFields));
      return WithPayloadSelectorFactory.include(fields);
    }
    if (CollectionUtils.isNotEmpty(excludeFields)) {
      return WithPayloadSelectorFactory.exclude(excludeFields);
    }
    return WithPayloadSelectorFactory.enable(true);
  }

  @Override
  public long count() {
    throw new UnsupportedOperationException("Count is not supported on the vector store");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Points.PointStruct toPoint(final T entity) {
    return Points.PointStruct.newBuilder()
        .setId(pointId(entity.getId()))
        .setVectors(buildNamedVectors(entity))
        .putAllPayload(VectorDbUtils.toValues(toPayload(entity)))
        .build();
  }

  /**
   * Builds the vectors for a Qdrant upsert, always keyed by physical vector field name — the same
   * name a semantic query addresses via {@code using}, so a collection needs its vectors declared
   * by name whether an entity carries one vector or several.
   */
  private static Points.Vectors buildNamedVectors(final VectorEntity entity) {
    final Map<String, Points.Vector> result = new HashMap<>();
    entity.getVectors().forEach((field, vector) -> result.put(field, VectorFactory.vector(vector)));
    return VectorsFactory.namedVectors(result);
  }

  private static Common.PointId pointId(final String id) {
    return PointIdFactory.id(UUID.fromString(id));
  }

  /** Translates a delete {@link Filter}: EQ (keyword match), or AND of them. */
  private static Common.Filter toDeleteFilter(final Filter filter) {
    final List<Common.Condition> must = new ArrayList<>();
    if (filter.getOp() == Operator.AND && filter.getValues() != null) {
      for (final Object child : filter.getValues()) {
        if (child instanceof final Filter childFilter) {
          final Common.Filter subFilter = toDeleteFilter(childFilter);
          if (subFilter != null) {
            must.addAll(subFilter.getMustList());
          }
        }
      }
    } else if (filter.getOp() == Operator.EQ
        && filter.getField() != null
        && filter.getValues() != null
        && !filter.getValues().isEmpty()) {
      must.add(
          ConditionFactory.matchKeyword(
              filter.getField(), String.valueOf(filter.getValues().getFirst())));
    }
    return must.isEmpty() ? null : Common.Filter.newBuilder().addAllMust(must).build();
  }

  private static Common.Filter buildQdrantFilter(final Filter filter) {
    if (filter == null) {
      return null;
    }

    final Operator op = filter.getOp();
    if (op == Operator.SEMANTIC_SEARCH) {
      return null;
    }

    final List<Object> values = filter.getValues();
    if (op == Operator.EQ) {
      return Common.Filter.newBuilder()
          .addMust(
              ConditionFactory.matchKeyword(filter.getField(), String.valueOf(values.getFirst())))
          .build();
    }

    if (op.isCompound() && CollectionUtils.isNotEmpty(values)) {
      final Common.Filter.Builder result = Common.Filter.newBuilder();
      for (final Object child : values) {
        final Common.Filter sub = buildQdrantFilter((Filter) child);
        if (sub != null) {
          if (op == Operator.AND) {
            result.addAllMust(sub.getMustList());
          } else {
            result.addAllShould(sub.getMustList());
          }
        }
      }
      if (result.getMustCount() > 0 || result.getShouldCount() > 0) {
        return result.build();
      }
    }

    return null;
  }

  private static List<Filter> extractSemanticFilters(final Filter filter) {
    final List<Filter> semantics = new ArrayList<>();
    if (filter == null) return semantics;
    if (filter.getOp() == Operator.SEMANTIC_SEARCH) {
      semantics.add(filter);
    } else if (filter.getOp().isCompound() && filter.getValues() != null) {
      for (final Object child : filter.getValues()) {
        if (child instanceof Filter childFilter) {
          semantics.addAll(extractSemanticFilters(childFilter));
        }
      }
    }
    return semantics;
  }

  private static double extractMinScore(final Filter semantic) {
    if (semantic == null || semantic.getAdditional() == null) return 0.0;
    final Object val = semantic.getAdditional().get("minScore");
    return val instanceof Number n ? n.doubleValue() : 0.0;
  }

  private static <R> R await(final ListenableFuture<R> future) {
    try {
      return future.get();
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Qdrant", exception);
    } catch (final ExecutionException exception) {
      throw new IllegalStateException("Qdrant request failed", exception.getCause());
    }
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
