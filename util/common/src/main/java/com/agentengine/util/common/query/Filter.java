package com.agentengine.util.common.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Filter {
    private String field;
    private Operator op;
    private List<Object> values;
    private Map<String, Object> additional;

    public String getField() {
        return field;
    }

    public void setField(final String field) {
        this.field = field;
    }

    public Operator getOp() {
        return op;
    }

    public void setOp(final Operator op) {
        this.op = op;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(final List<Object> values) {
        this.values = values;
    }

    public Filter addValue(final Object value) {
        values = values == null ? new ArrayList<>() : values;
        values.add(value);
        return this;
    }

    public Filter withField(final String field) {
        this.field = field;
        return this;
    }

    public Filter withOp(final Operator op) {
        this.op = op;
        return this;
    }

    public Map<String, Object> getAdditional() {
        return additional;
    }

    public void setAdditional(final Map<String, Object> additional) {
        this.additional = additional;
    }

    public Filter withAdditional(final Map<String, Object> additional) {
        this.additional = additional;
        return this;
    }
}
