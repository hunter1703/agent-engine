package com.agentengine.util.vectordb;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Operator;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.OperationType;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.Utils;
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
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Qdrant storage of one entity class, as {@link QdrantBackend#getEntityStore} returns it. */
public final class QdrantEntityStore<T extends VectorEntity> implements VectorEntityStore<T> {

  private static final Logger LOG = LoggerFactory.getLogger(QdrantEntityStore.class);
  private static final int DEFAULT_UPSERT_BATCH_SIZE = 50;
  private static final int VECTOR_SIZE = 768;
  private static final int SCROLL_PAGE_SIZE = 256;

  private final VectorRepositorySpec<T> spec;
  private final VectorDbClientFactory clientFactory;
  private final LazyLoader<Map<String, String>> fieldVsVectorName;
  private final LazyLoader<Set<String>> indexedFields;

  QdrantEntityStore(final VectorRepositorySpec<T> spec, final VectorDbClientFactory clientFactory) {
    this.spec = spec;
    this.clientFactory = clientFactory;
    this.fieldVsVectorName = new LazyLoader<>(() -> VectorDbUtils.vectorNames(spec.entityClass()));
    this.indexedFields =
        new LazyLoader<>(
            () ->
                Utils.fieldsAnnotatedWith(spec.entityClass(), Indexed.class).stream()
                    .filter(field -> !field.getAnnotation(Indexed.class).vector())
                    .map(Field::getName)
                    .collect(Collectors.toSet()));
  }

  public VectorRepositorySpec<T> spec() {
    return spec;
  }

  @Override
  public Class<T> entityClass() {
    return spec.entityClass();
  }

  @Override
  public String vectorName(final String field) {
    return fieldVsVectorName.get().get(field);
  }

  @Override
  public String newId() {
    return UUID.randomUUID().toString();
  }

  @Override
  public T insert(final T entity) {
    if (StringUtils.isBlank(entity.getId())) {
      entity.setId(newId());
    }
    await(client().upsertAsync(collectionName(), List.of(toPoint(entity))));
    return entity;
  }

  @Override
  public List<T> insertMany(final List<T> entities) {
    for (final List<T> batch : CollectionUtils.batches(entities, DEFAULT_UPSERT_BATCH_SIZE)) {
      final List<Points.PointStruct> points = new ArrayList<>(batch.size());
      for (final T entity : batch) {
        if (StringUtils.isBlank(entity.getId())) {
          entity.setId(newId());
        }
        points.add(toPoint(entity));
      }
      await(client().upsertAsync(collectionName(), points));
    }
    return entities;
  }

  @Override
  public T findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    return retrievePoints(List.of(id), includeFields, excludeFields).get(id);
  }

  @Override
  public Map<String, T> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    return retrievePoints(ids, includeFields, excludeFields);
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    final Filter filter = query == null ? null : query.getFilter();
    final String embeddingModelId =
        filter == null
            ? null
            : CollectionUtils.getStringValueFromMap(
                filter.getAdditional(), FIELD_EMBEDDING_MODEL_ID);
    return findBySemanticQuery(
        new Query(query).withFilter(rewriteSemanticFilter(filter, embeddingModelId)));
  }

  /**
   * Qdrant has no conditional writes, so {@code expectedVersion} is checked by reading the point
   * first: a write landing between the check and this one is overwritten.
   */
  @Override
  public T replace(final T entity, final Long expectedVersion, final boolean upsert) {
    if (!upsert || expectedVersion != null) {
      final T existing = findById(entity.getId(), List.of(BaseEntity.FIELD_VERSION), null);
      if (existing == null && !upsert) {
        throw new AssetNotFoundException(spec.entityClass().getSimpleName(), entity.getId());
      }
      if (expectedVersion != null
          && (existing == null || existing.getVersion() != expectedVersion)) {
        throw new StaleStateException(entity.getId(), expectedVersion);
      }
    }
    await(client().upsertAsync(collectionName(), List.of(toPoint(entity))));
    return entity;
  }

  @Override
  public T findOneAndUpdate(final Query query, final Update update) {
    final List<String> updatedIds = applyUpdate(requireFilter(query.getFilter()), update, 1);
    return updatedIds.isEmpty()
        ? null
        : findById(updatedIds.getFirst(), query.getIncludeFields(), query.getExcludeFields());
  }

  @Override
  public long updateOne(final Filter filter, final Update update) {
    return applyUpdate(requireFilter(filter), update, 1).size();
  }

  /** Qdrant has no conditional writes, so it cannot tell an update from an insert atomically. */
  @Override
  public T upsertOne(final Filter filter, final Update update) {
    throw new UnsupportedOperationException("Qdrant collections do not support upserts");
  }

  @Override
  public long updateMany(final Filter filter, final Update update) {
    final Common.Filter qdrantFilter = requireFilter(filter);
    if (!setsOnly(update)) {
      return applyUpdate(qdrantFilter, update, -1).size();
    }
    final Map<String, Object> payload = new LinkedHashMap<>();
    for (final Operation operation : update.operations()) {
      payload.put(operation.field(), operation.value());
    }
    final long matched = await(client().countAsync(collectionName(), qdrantFilter, true));
    await(
        client()
            .setPayloadAsync(
                collectionName(), VectorDbUtils.toValues(payload), qdrantFilter, true, null, null));
    return matched;
  }

  @Override
  public boolean deleteById(final String id) {
    await(client().deleteAsync(collectionName(), idFilter(id)));
    return true;
  }

  /**
   * Deletes the point only while its payload holds {@code version}. Qdrant reports no count for a
   * delete, so whether it did is read back afterwards.
   */
  @Override
  public boolean delete(final String id, final long version) {
    await(
        client()
            .deleteAsync(
                collectionName(),
                Common.Filter.newBuilder()
                    .addMust(ConditionFactory.hasId(pointId(id)))
                    .addMust(ConditionFactory.match(BaseEntity.FIELD_VERSION, version))
                    .build()));
    return findById(id, List.of(BaseEntity.FIELD_ID), null) == null;
  }

  @Override
  public void deleteByFilter(final Filter filter) {
    final Common.Filter qdrantFilter =
        filter == null ? Common.Filter.getDefaultInstance() : buildQdrantFilter(filter);
    if (qdrantFilter == null) {
      throw new IllegalArgumentException("Delete query has no supported filter");
    }
    await(client().deleteAsync(collectionName(), qdrantFilter));
  }

  /**
   * Creates the current customer's collection, with one named vector of {@value #VECTOR_SIZE} per
   * vector field, when it does not exist yet, and a payload index on each indexed field.
   */
  @Override
  public void setup() {
    final QdrantClient client = client();
    final String name = collectionName();
    if (!await(client.collectionExistsAsync(name))) {
      final Map<String, Collections.VectorParams> vectors = new LinkedHashMap<>();
      for (final String vectorName : fieldVsVectorName.get().values()) {
        vectors.put(
            vectorName,
            Collections.VectorParams.newBuilder()
                .setSize(VECTOR_SIZE)
                .setDistance(Collections.Distance.Cosine)
                .build());
      }
      await(client.createCollectionAsync(name, vectors));
    }
    // Qdrant Cloud rejects a filter on an unindexed field; creating an existing index is a no-op.
    for (final String field : indexedFields.get()) {
      await(
          client.createPayloadIndexAsync(
              name, field, Collections.PayloadSchemaType.Keyword, null, true, null, null));
    }
    if (spec.entityClass().isAnnotationPresent(Permissioned.class)) {
      // Access checks filter by grant; applying an access list filters by a range on its version.
      await(
          client.createPayloadIndexAsync(
              name,
              BaseEntity.FIELD_ACL_GRANTS,
              Collections.PayloadSchemaType.Keyword,
              null,
              true,
              null,
              null));
      await(
          client.createPayloadIndexAsync(
              name,
              BaseEntity.FIELD_ACL_VERSION,
              Collections.PayloadSchemaType.Integer,
              null,
              true,
              null,
              null));
    }
  }

  private PaginatedResult<T> findBySemanticQuery(final Query query) {
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
    if (page.getOffset() > 0) {
      request.setOffset(page.getOffset());
    }
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
    final List<T> results = points.stream().map(this::toEntity).toList();
    return PaginatedResult.create(results, page, null);
  }

  private Filter rewriteSemanticFilter(final Filter filter, final String embeddingModelId) {
    if (filter == null) {
      return null;
    }
    final Operator op = filter.getOp();
    if (op == Operator.SEMANTIC_SEARCH) {
      final String first =
          Objects.requireNonNull(CollectionUtils.getFirst(filter.getValues())).toString();
      final String field = filter.getField();
      // Per-filter embeddingModelId (in additional) takes precedence over the global fallback,
      // allowing different semantic search fields to use different embedding models.
      final String modelId =
          CollectionUtils.getStringValueFromMap(filter.getAdditional(), FIELD_EMBEDDING_MODEL_ID);
      final String resolvedModelId = StringUtils.isNotBlank(modelId) ? modelId : embeddingModelId;
      if (StringUtils.isBlank(resolvedModelId)) {
        return Filters.eq(field, first);
      }
      final String vectorName = fieldVsVectorName.get().get(field);
      if (vectorName == null) {
        throw new IllegalArgumentException(
            spec.entityClass().getSimpleName() + "." + field + " is not a vector field");
      }
      return Filters.semanticSearch(vectorName, spec.queryEmbedder().apply(resolvedModelId, first));
    }
    if (!op.isCompound()) {
      return filter;
    }
    final List<Object> subFilters = CollectionUtils.nullSafeList(filter.getValues());
    final List<Filter> updatedSubFilters = new ArrayList<>();
    for (final Object subFilterObj : subFilters) {
      final Filter subFilter;
      if (subFilterObj instanceof Filter filterValue) {
        subFilter = filterValue;
      } else {
        //noinspection unchecked
        subFilter = JsonUtils.fromMap((Map<String, Object>) subFilterObj, Filter.class);
      }
      updatedSubFilters.add(rewriteSemanticFilter(subFilter, embeddingModelId));
    }
    return new Filter().withOp(op).withValues(updatedSubFilters);
  }

  private Map<String, T> retrievePoints(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    final List<Common.PointId> pointIds = ids.stream().map(QdrantEntityStore::pointId).toList();
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
      final T entity = toEntity(point.getPayloadMap(), pointIdToString(point.getId()));
      result.put(entity.getId(), entity);
    }
    return result;
  }

  private String collectionName() {
    return spec.name() + "_" + Context.currentCustomerId().orElseThrow();
  }

  private QdrantClient client() {
    return clientFactory.getClient(spec.clientType(), Context.currentCustomerId().orElseThrow());
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

  /**
   * Whether every operation assigns a non-null value, so one filtered write can apply it. An update
   * that counts a new version never is, since a version is incremented per point.
   */
  private static boolean setsOnly(final Update update) {
    for (final Operation operation : update.operations()) {
      if (operation.type() != OperationType.SET || operation.value() == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * Applies the update to up to {@code limit} points matching the filter (all when negative) and
   * returns the ids it touched. Qdrant has no server-side update operators, so each point's touched
   * fields are read, changed and written back a page at a time; concurrent updates to the same
   * point can overwrite one another.
   */
  private List<String> applyUpdate(
      final Common.Filter filter, final Update update, final int limit) {
    final Set<String> fields = new LinkedHashSet<>();
    for (final Operation operation : update.operations()) {
      fields.add(operation.field());
    }
    final List<String> updatedIds = new ArrayList<>();
    forEachPage(
        filter,
        limit,
        fields,
        page -> {
          for (final Points.RetrievedPoint point : page) {
            applyToPoint(point, update, fields);
            updatedIds.add(pointIdToString(point.getId()));
          }
        });
    return updatedIds;
  }

  private void applyToPoint(
      final Points.RetrievedPoint point, final Update update, final Set<String> fields) {
    final Map<String, Object> payload = VectorDbUtils.fromValues(point.getPayloadMap());
    for (final Operation operation : update.operations()) {
      applyOperation(payload, operation);
    }
    final Map<String, Object> changed = new LinkedHashMap<>();
    final List<String> removed = new ArrayList<>();
    for (final String field : fields) {
      final Object value = payload.get(field);
      if (value == null) {
        removed.add(field);
      } else {
        changed.put(field, value);
      }
    }
    if (!changed.isEmpty()) {
      await(
          client()
              .setPayloadAsync(
                  collectionName(),
                  VectorDbUtils.toValues(changed),
                  point.getId(),
                  true,
                  null,
                  null));
    }
    if (!removed.isEmpty()) {
      await(
          client().deletePayloadAsync(collectionName(), removed, point.getId(), true, null, null));
    }
  }

  private static void applyOperation(final Map<String, Object> payload, final Operation operation) {
    final String field = operation.field();
    switch (operation.type()) {
      case SET -> payload.put(field, operation.value());
      case UNSET -> payload.remove(field);
      case INC -> payload.put(field, increment(payload.get(field), (Number) operation.value()));
      case ADD_TO_SET -> {
        final Set<Object> merged = new LinkedHashSet<>();
        if (payload.get(field) instanceof Collection<?> existing) {
          merged.addAll(existing);
        }
        merged.addAll((Collection<?>) operation.value());
        payload.put(field, new ArrayList<>(merged));
      }
      case REMOVE_FROM_SET -> {
        if (payload.get(field) instanceof Collection<?> existing) {
          final List<Object> remaining = new ArrayList<>(existing);
          remaining.removeAll((Collection<?>) operation.value());
          payload.put(field, remaining);
        }
      }
      default ->
          throw new UnsupportedOperationException(
              "Unsupported vector store update operation: " + operation.type());
    }
  }

  private static Number increment(final Object current, final Number delta) {
    final Number base = current instanceof Number number ? number : 0L;
    final boolean decimal = isDecimal(base) || isDecimal(delta);
    return decimal
        ? (Number) (base.doubleValue() + delta.doubleValue())
        : (Number) (base.longValue() + delta.longValue());
  }

  private static boolean isDecimal(final Number number) {
    return number instanceof Double || number instanceof Float;
  }

  /** Feeds the handler the points matching the filter, carrying only the given payload fields. */
  private void forEachPage(
      final Common.Filter filter,
      final int limit,
      final Collection<String> payloadFields,
      final Consumer<List<Points.RetrievedPoint>> handler) {
    int handled = 0;
    Common.PointId offset = null;
    while (limit < 0 || handled < limit) {
      final int pageSize =
          limit < 0 ? SCROLL_PAGE_SIZE : Math.min(SCROLL_PAGE_SIZE, limit - handled);
      final Points.ScrollPoints.Builder request =
          Points.ScrollPoints.newBuilder()
              .setCollectionName(collectionName())
              .setFilter(filter)
              .setLimit(pageSize)
              .setWithPayload(WithPayloadSelectorFactory.include(List.copyOf(payloadFields)));
      if (offset != null) {
        request.setOffset(offset);
      }
      final Points.ScrollResponse response = await(client().scrollAsync(request.build()));
      handler.accept(response.getResultList());
      handled += response.getResultCount();
      if (!response.hasNextPageOffset()) {
        break;
      }
      offset = response.getNextPageOffset();
    }
  }

  private static Common.Filter idFilter(final String id) {
    return Common.Filter.newBuilder().addMust(ConditionFactory.hasId(pointId(id))).build();
  }

  private static Common.Filter requireFilter(final Filter filter) {
    final Common.Filter qdrantFilter = buildQdrantFilter(filter);
    if (qdrantFilter == null) {
      throw new IllegalArgumentException("Update requires a supported filter");
    }
    return qdrantFilter;
  }

  private Points.PointStruct toPoint(final T entity) {
    return Points.PointStruct.newBuilder()
        .setId(pointId(entity.getId()))
        .setVectors(buildNamedVectors(entity))
        .putAllPayload(VectorDbUtils.toValues(toFullPayload(entity)))
        .build();
  }

  /**
   * Builds the vectors for a Qdrant upsert, always keyed by physical vector field name — the same
   * name a semantic query addresses via {@code using}, so a collection needs its vectors declared
   * by name whether an entity carries one vector or several.
   */
  private static Points.Vectors buildNamedVectors(final VectorEntity entity) {
    final Map<String, Points.Vector> result = new HashMap<>();
    if (entity.getVectors() != null) {
      entity
          .getVectors()
          .forEach((field, vector) -> result.put(field, VectorFactory.vector(vector)));
    }
    return VectorsFactory.namedVectors(result);
  }

  private T toEntity(final Points.ScoredPoint point) {
    return toEntity(point.getPayloadMap(), point.hasId() ? pointIdToString(point.getId()) : null);
  }

  private T toEntity(final Map<String, JsonWithInt.Value> values, final String id) {
    final Map<String, Object> payload = VectorDbUtils.fromValues(values);
    final T entity = spec.fromPayload().apply(payload);
    entity.setId(id);
    if (payload.get(BaseEntity.FIELD_CREATED_TIME) instanceof Number createdTime) {
      entity.setCreatedTime(createdTime.longValue());
    }
    if (payload.get(BaseEntity.FIELD_UPDATED_TIME) instanceof Number updatedTime) {
      entity.setUpdatedTime(updatedTime.longValue());
    }
    if (payload.get(BaseEntity.FIELD_VERSION) instanceof Number version) {
      entity.setVersion(version.longValue());
    }
    if (payload.get(BaseEntity.FIELD_CREATED_BY) instanceof String createdBy) {
      entity.setCreatedBy(createdBy);
    }
    entity.setAcl(
        JsonUtils.fromMap(CollectionUtils.getMapFromMap(payload, BaseEntity.FIELD_ACL), Acl.class));
    if (payload.containsKey(BaseEntity.FIELD_TAGS)) {
      entity.setTags(CollectionUtils.getListFromMap(payload, BaseEntity.FIELD_TAGS));
    }
    return entity;
  }

  /** The entity's own payload plus the {@link BaseEntity} fields every stored entity carries. */
  private Map<String, Object> toFullPayload(final T entity) {
    final Map<String, Object> payload = new HashMap<>(spec.toPayload().apply(entity));
    payload.put(BaseEntity.FIELD_CREATED_TIME, entity.getCreatedTime());
    payload.put(BaseEntity.FIELD_UPDATED_TIME, entity.getUpdatedTime());
    payload.put(BaseEntity.FIELD_VERSION, entity.getVersion());
    payload.put(BaseEntity.FIELD_CREATED_BY, Objects.requireNonNullElse(entity.getCreatedBy(), ""));
    payload.put(BaseEntity.FIELD_ACL, JsonUtils.toMap(entity.getAcl()));
    payload.put(BaseEntity.FIELD_TAGS, CollectionUtils.nullSafeList(entity.getTags()));
    return payload;
  }

  private static String pointIdToString(final Common.PointId pointId) {
    return pointId.hasUuid() ? pointId.getUuid() : String.valueOf(pointId.getNum());
  }

  private static Common.PointId pointId(final String id) {
    return PointIdFactory.id(UUID.fromString(id));
  }

  private static Common.Filter buildQdrantFilter(final Filter filter) {
    if (filter == null || filter.getOp() == Operator.SEMANTIC_SEARCH) {
      return null;
    }

    final Operator op = filter.getOp();
    final List<Object> values = filter.getValues();
    final boolean onIds = op == Operator.EQ || op == Operator.IN;
    if (onIds && BaseEntity.FIELD_ID.equals(filter.getField())) {
      return Common.Filter.newBuilder()
          .addMust(
              ConditionFactory.hasId(
                  values.stream().map(value -> pointId(String.valueOf(value))).toList()))
          .build();
    }
    if (op == Operator.EQ) {
      final Object value = values.getFirst();
      return Common.Filter.newBuilder()
          .addMust(
              value instanceof Long || value instanceof Integer
                  ? ConditionFactory.match(filter.getField(), ((Number) value).longValue())
                  : ConditionFactory.matchKeyword(filter.getField(), String.valueOf(value)))
          .build();
    }

    if (op == Operator.IN && filter.getField() != null && CollectionUtils.isNotEmpty(values)) {
      final List<String> keywords = values.stream().map(String::valueOf).toList();
      return Common.Filter.newBuilder()
          .addMust(ConditionFactory.matchKeywords(filter.getField(), keywords))
          .build();
    }

    if (op == Operator.LT || op == Operator.LTE || op == Operator.GT || op == Operator.GTE) {
      final double bound = ((Number) values.getFirst()).doubleValue();
      final Common.Range.Builder range = Common.Range.newBuilder();
      switch (op) {
        case LT -> range.setLt(bound);
        case LTE -> range.setLte(bound);
        case GT -> range.setGt(bound);
        default -> range.setGte(bound);
      }
      return Common.Filter.newBuilder()
          .addMust(ConditionFactory.range(filter.getField(), range.build()))
          .build();
    }

    if (op.isCompound()) {
      final Common.Filter.Builder result = Common.Filter.newBuilder();
      for (final Object child : CollectionUtils.nullSafeList(values)) {
        final Filter childFilter = (Filter) child;
        final Common.Filter sub = buildQdrantFilter(childFilter);
        if (sub == null) {
          if (isSemanticOnly(childFilter)) {
            continue;
          }
          throw new IllegalArgumentException("Unsupported vector store filter: " + childFilter);
        }
        switch (op) {
          case AND -> result.addMust(ConditionFactory.filter(sub));
          case OR -> result.addShould(ConditionFactory.filter(sub));
          case NOT -> result.addMustNot(ConditionFactory.filter(sub));
          default ->
              throw new IllegalArgumentException("Unsupported vector store filter: " + filter);
        }
      }
      return result.getMustCount() > 0
              || result.getShouldCount() > 0
              || result.getMustNotCount() > 0
          ? result.build()
          : null;
    }

    throw new IllegalArgumentException("Unsupported vector store filter: " + filter);
  }

  private static boolean isSemanticOnly(final Filter filter) {
    if (filter.getOp() == Operator.SEMANTIC_SEARCH) {
      return true;
    }
    return filter.getOp().isCompound()
        && CollectionUtils.nullSafeList(filter.getValues()).stream()
            .allMatch(child -> child instanceof Filter childFilter && isSemanticOnly(childFilter));
  }

  private static List<Filter> extractSemanticFilters(final Filter filter) {
    final List<Filter> semantics = new ArrayList<>();
    if (filter == null) {
      return semantics;
    }
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
    if (semantic == null || semantic.getAdditional() == null) {
      return 0.0;
    }
    return semantic.getAdditional().get("minScore") instanceof Number number
        ? number.doubleValue()
        : 0.0;
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
}
