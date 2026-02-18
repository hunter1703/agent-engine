package com.agentengine.engine.api.query;

import java.util.Arrays;
import java.util.List;

public final class Filters {

    private Filters() {
    }

    public static Filter eq(final String field, final Object value) {
        final Filter filter = new Filter();
        filter.setField(field);
        filter.setOp(Operator.EQ);
        filter.setValues(List.of(value));
        return filter;
    }

    public static Filter and(final Filter... filters) {
        final Filter filter = new Filter();
        filter.setOp(Operator.AND);
        Arrays.asList(filters).forEach(filter::addValue);
        return filter;
    }
}
