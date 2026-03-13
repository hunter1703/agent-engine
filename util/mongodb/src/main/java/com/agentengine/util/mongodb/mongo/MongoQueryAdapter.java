package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Operator;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class MongoQueryAdapter {

  private MongoQueryAdapter() {
  }

  public static Bson toBson(Filter filter) {
    if (filter == null) {
      return new Document();
    }
    if (filter.getOp().isCompound()) {
      final List<Bson> subFilters = filter.getValues().stream().map(value -> toBson((Filter) value)).toList();
      return switch (filter.getOp()) {
        case AND -> Filters.and(subFilters.toArray(new Bson[0]));
        case OR -> Filters.or(subFilters.toArray(new Bson[0]));
        case NOT -> Filters.not(Objects.requireNonNull(CollectionUtils.getFirst(subFilters)));
        default -> throw new IllegalArgumentException("Unsupported compound operator: " + filter.getOp());
      };
    } else {
      return toSimpleFilterBson(filter);
    }
  }

  private static Bson toSimpleFilterBson(Filter filter) {
    String field = filter.getField();
    List<Object> value = filter.getValues();
    Operator op = filter.getOp();

    if (op == null) {
      throw new IllegalArgumentException("Filter operator is required for field: " + field);
    }

    final Object first = CollectionUtils.getFirst(value);
    return switch (op) {
      case EQ -> Filters.eq(field, first);
      case NE -> Filters.ne(field, first);
      case GT -> Filters.gt(field, Objects.requireNonNull(first));
      case GTE -> Filters.gte(field, Objects.requireNonNull(first));
      case LT -> Filters.lt(field, Objects.requireNonNull(first));
      case LTE -> Filters.lte(field, Objects.requireNonNull(first));
      case IN -> Filters.in(field, (Iterable<?>) value);
      case NIN -> Filters.nin(field, (Iterable<?>) value);
      case CONTAINS -> Filters.regex(field, Pattern.quote(String.valueOf(first)), "i");
      case EXISTS -> Filters.exists(field);
      case NOT_EXISTS -> Filters.exists(field, false);
      default -> throw new IllegalArgumentException("Unsupported operator: " + op);
    };
  }

  public static Bson toSortBson(Sort sort) {
    if (sort == null || sort.getField() == null) {
      return null;
    }
    return switch (sort.getOrder()) {
      case ASC -> Sorts.ascending(sort.getField());
      case DESC -> Sorts.descending(sort.getField());
      case UNKNOWN -> Sorts.ascending(sort.getField()); // Defaulting to ascending
    };
  }

  public static Bson toProjectionBson(final Query query) {
    if (query == null) {
      return null;
    }
    final List<String> includes = CollectionUtils.nullSafeList(query.getIncludeFields());
    final List<String> excludes = CollectionUtils.nullSafeList(query.getExcludeFields());
    if (!includes.isEmpty() && !excludes.isEmpty()) {
      return Projections.fields(Projections.include(includes.toArray(new String[0])), Projections.exclude(excludes.toArray(new String[0])));
    }
    if (!includes.isEmpty()) {
      return Projections.include(includes.toArray(new String[0]));
    }
    if (!excludes.isEmpty()) {
      return Projections.exclude(excludes.toArray(new String[0]));
    }
    return null;
  }
}
