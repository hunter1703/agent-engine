package com.agentengine.engine.mongo;

import com.agentengine.engine.api.query.Filter;
import com.agentengine.engine.api.query.Operator;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MongoQueryAdapter {

    private MongoQueryAdapter() {
    }

    public static Bson toBson(Filter filter) {
        if (filter == null) {
            return new Document();
        }
        if (filter.getOp().isCompound()) {
            final List<Bson> subFilters = filter.getValues().stream()
                    .map(value -> toBson((Filter) value))
                    .toList();
            return switch (filter.getOp()) {
                case AND -> Filters.and(subFilters.toArray(new Bson[0]));
                case OR -> Filters.or(subFilters.toArray(new Bson[0]));
                case NOT -> Filters.not(Objects.requireNonNull(CollectionUtils.getFirst(subFilters)));
                default -> throw new IllegalArgumentException(STR."Unsupported compound operator: \{filter.getOp()}");
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
            return Filters.eq(field, value);
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
}
