package com.agentengine.util.vectordb;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.repository.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public abstract class VectorStore<T extends BaseEntity> implements Repository<T> {
  public static final String FIELD_EMBEDDING_MODEL_ID = "embeddingModelId";

  private final BiFunction<String, String, float[]> embeddingGenerator;

  protected VectorStore(final BiFunction<String, String, float[]> embeddingGenerator) {
    this.embeddingGenerator = embeddingGenerator;
  }

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
      // Apply the <field> → <field>Vector naming convention so the rewritten filter
      // references the physical vector field name in the store (e.g. "text" → "textVector").
      final String vectorField = StringUtils.isBlank(field) ? null : field + "Vector";
      // Per-filter embeddingModelId (in additional) takes precedence over the global fallback,
      // allowing different semantic search fields to use different embedding models.
      final String modelId =
          CollectionUtils.getStringValueFromMap(filter.getAdditional(), FIELD_EMBEDDING_MODEL_ID);
      final String resolvedModelId = StringUtils.isNotBlank(modelId) ? modelId : embeddingModelId;
      if (StringUtils.isBlank(resolvedModelId)) {
        return Filters.eq(field, first);
      }
      return Filters.semanticSearch(vectorField, embeddingGenerator.apply(resolvedModelId, first));
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
