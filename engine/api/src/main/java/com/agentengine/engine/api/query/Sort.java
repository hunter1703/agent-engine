package com.agentengine.engine.api.query;

public class Sort {

  private String field;
  private Order order;

  public Sort() {}

  public Sort(String field, Order order) {
    this.field = field;
    this.order = order;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public Order getOrder() {
    return order;
  }

  public void setOrder(Order order) {
    this.order = order;
  }

  public Sort withField(String field) {
    this.field = field;
    return this;
  }

  public Sort withOrder(Order order) {
    this.order = order;
    return this;
  }

  public enum Order {
    ASC,
    DESC,
    UNKNOWN;

    public static Order valueOfOrDefault(String value) {
      if (value == null) {
        return UNKNOWN;
      }
      try {
        return Order.valueOf(value.toUpperCase());
      } catch (IllegalArgumentException e) {
        return UNKNOWN;
      }
    }
  }
}
