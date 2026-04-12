package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Operator;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class MongoUtils {

    private MongoUtils() {}

    public static Bson toBsonUpdate(final Update update) {
        Objects.requireNonNull(update, "update");
        final List<Bson> updates =
                update.operations().stream().map(MongoUtils::toBsonOperation).toList();
        return Updates.combine(updates);
    }

    private static Bson toBsonOperation(final Operation operation) {
        Objects.requireNonNull(operation, "operation");
        return switch (operation.type()) {
            case SET -> Updates.set(operation.field(), operation.value());
            case UNSET -> Updates.unset(operation.field());
            default -> throw new IllegalStateException("Unexpected value: " + operation.type());
        };
    }

    public static Bson toBson(Filter filter) {
        if (filter == null) {
            return new Document();
        }
        if (filter.getOp().isCompound()) {
            final List<Bson> subFilters = CollectionUtils.nullSafeList(filter.getValues()).stream()
                    .filter(Objects::nonNull)
                    .map(value -> toBson(convertToFilter(value)))
                    .toList();
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

    /**
     * Converts an object to a Filter using JsonUtils.fromMap for proper deserialization.
     */
    private static Filter convertToFilter(Object value) {
        if (value instanceof Filter) {
            return (Filter) value;
        }
        if (value instanceof Map<?, ?> map) {
            // Use JsonUtils.fromMap to convert LinkedHashMap to Filter
            return JsonUtils.fromMap((Map<String, Object>) map, Filter.class);
        }
        throw new IllegalArgumentException("Cannot convert value to Filter: " + value.getClass());
    }

    private static Bson toSimpleFilterBson(Filter filter) {
        String field = normalizeField(filter.getField());
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
        final String field = normalizeField(sort.getField());
        return switch (sort.getOrder()) {
            case ASC -> Sorts.ascending(field);
            case DESC -> Sorts.descending(field);
            case UNKNOWN -> Sorts.ascending(field); // Defaulting to ascending
        };
    }

    public static Bson toProjectionBson(final Query query) {
        if (query == null) {
            return null;
        }

        return getProjection(query.getIncludeFields(), query.getExcludeFields());
    }

    public static Bson getProjection(List<String> includes, List<String> excludes) {
        includes = CollectionUtils.nullSafeList(includes);
        excludes = CollectionUtils.nullSafeList(excludes);
        if (!includes.isEmpty() && !excludes.isEmpty()) {
            throw new IllegalArgumentException(
                    "MongoDB projections cannot mix inclusion and exclusion fields: includes="
                            + includes
                            + ", excludes="
                            + excludes);
        }
        if (!includes.isEmpty()) {
            return Projections.include(includes.toArray(new String[0]));
        }
        if (!excludes.isEmpty()) {
            return Projections.exclude(excludes.toArray(new String[0]));
        }
        return null;
    }

    private static String normalizeField(final String field) {
        if (BaseEntity.FIELD_ID.equals(field)) {
            return "_id";
        }
        return field;
    }
}
