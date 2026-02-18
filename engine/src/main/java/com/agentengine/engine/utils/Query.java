package com.agentengine.engine.utils;

import org.bson.conversions.Bson;

public class Query {
  // TODO: make this a java bean
  private Bson filter;
  private Page page;

  public Page getPage() {
    return page;
  }

  public void setPage(final Page page) {
    this.page = page;
  }

  public Bson getFilter() {
    return filter;
  }

  public void setFilter(final Bson filter) {
    this.filter = filter;
  }

  public Query withFilter(final Bson filter) {
    this.filter = filter;
    return this;
  }

  public Query withPage(final Page page) {
    this.page = page;
    return this;
  }
}
