package com.agentengine.engine.api.query;

import java.util.Arrays;
import java.util.List;

public class Filters {

  public static Filter eq(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.EQ);
  }

  public static Filter ne(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.NE);
  }

  public static Filter gt(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.GT);
  }

  public static Filter gte(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.GTE);
  }

  public static Filter lt(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.LT);
  }

  public static Filter lte(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.LTE);
  }

  public static Filter in(String field, List<Object> values) {
    Filter filter = new Filter().withField(field).withOp(Operator.IN);
    if (values != null) {
      filter.setValues(values);
    }
    return filter;
  }

  public static Filter nin(String field, List<Object> values) {
    Filter filter = new Filter().withField(field).withOp(Operator.NIN);
    if (values != null) {
      filter.setValues(values);
    }
    return filter;
  }

  public static Filter contains(String field, Object value) {
    return new Filter().withField(field).addValue(value).withOp(Operator.CONTAINS);
  }

  public static Filter exists(String field) {
    return new Filter().withField(field).withOp(Operator.EXISTS);
  }

  public static Filter notExists(String field) {
    return new Filter().withField(field).withOp(Operator.NOT_EXISTS);
  }

  public static Filter and(Filter... filters) {
    Filter filter = new Filter().withOp(Operator.AND);
    if (filters != null) {
      filter.setValues(Arrays.asList((Object[]) filters));
    }
    return filter;
  }

  public static Filter or(Filter... filters) {
    Filter filter = new Filter().withOp(Operator.OR);
    if (filters != null) {
      filter.setValues(Arrays.asList((Object[]) filters));
    }
    return filter;
  }

  public static Filter not(Filter filter) {
    return new Filter().withOp(Operator.NOT).addValue(filter);
  }
}
