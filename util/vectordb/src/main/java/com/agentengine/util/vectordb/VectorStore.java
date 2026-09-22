package com.agentengine.util.vectordb;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Utils;
import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Operator;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.Repository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public abstract class VectorStore<T extends BaseEntity> implements Repository<T> {
  public static final String FIELD_EMBEDDING_MODEL_ID = "embeddingModelId";

  protected final Class<T> entityClass;
  private final LazyLoader<Map<String, String>> fieldVsVectorName;
  private final LazyLoader<Set<String>> indexedFields;
  private final BiFunction<String, String, float[]> embeddingGenerator;
  private final VectorStoreClientType clientType;

  protected VectorStore(
      final Class<T> entityClass,
      final VectorStoreClientType clientType,
      final BiFunction<String, String, float[]> embeddingGenerator) {
    this.entityClass = entityClass;
    this.fieldVsVectorName = new LazyLoader<>(() -> VectorDbUtils.vectorNames(entityClass));
    this.indexedFields =
        new LazyLoader<>(
            () ->
                Utils.fieldsAnnotatedWith(entityClass, Indexed.class).stream()
                    .filter(field -> !field.getAnnotation(Indexed.class).vector())
                    .map(Field::getName)
                    .collect(Collectors.toSet()));
    this.embeddingGenerator = embeddingGenerator;
    this.clientType = clientType;
  }

  protected Map<String, String> getFieldVsVectorName() {
    return fieldVsVectorName.get();
  }

  protected Set<String> getIndexedFields() {
    return indexedFields.get();
  }

  VectorStoreClientType clientType() {
    return clientType;
  }

  protected abstract void setup(int vectorSize);

  @Override
  public PaginatedResult<T> findByQuery(Query query) {
    final Filter filter = query == null ? null : query.getFilter();
    final String embeddingModelId =
        filter == null
            ? null
            : CollectionUtils.getStringValueFromMap(
                filter.getAdditional(), FIELD_EMBEDDING_MODEL_ID);
    return findBySemanticQueryInternal(
        new Query(query).withFilter(rewriteSemanticFilter(filter, embeddingModelId)));
  }

  protected abstract PaginatedResult<T> findBySemanticQueryInternal(Query query);

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
            entityClass.getSimpleName() + "." + field + " is not a vector field");
      }
      return Filters.semanticSearch(vectorName, embeddingGenerator.apply(resolvedModelId, first));
    }
    if (!op.isCompound()) {
      return filter;
    }
    final List<Object> subFilters = CollectionUtils.nullSafeList(filter.getValues());
    final List<Filter> updatedSubFilters = new ArrayList<>();
    for (final Object subFilterObj : subFilters) {
      Filter subFilter;
      if (subFilterObj instanceof Filter) {
        subFilter = (Filter) subFilterObj;
      } else {
        //noinspection unchecked
        subFilter = JsonUtils.fromMap((Map<String, Object>) subFilterObj, Filter.class);
      }
      updatedSubFilters.add(rewriteSemanticFilter(subFilter, embeddingModelId));
    }
    return new Filter().withOp(op).withValues(updatedSubFilters);
  }
}
