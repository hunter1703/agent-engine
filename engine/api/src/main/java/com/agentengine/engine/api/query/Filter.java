package com.agentengine.engine.api.query;

import java.util.ArrayList;
import java.util.List;

public class Filter {
        private String field;
        private Operator op;
        private List<Object> values;

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
}
