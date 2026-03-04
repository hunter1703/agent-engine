package com.agentengine.engine.api.query;

public class Query {
  private Filter filter;
  private Page page;
  private Sort sort;

  public Query() {}

  public Filter getFilter() {
    return filter;
  }

  public void setFilter(Filter filter) {
    this.filter = filter;
  }

  public Page getPage() {
    return page;
  }

  public void setPage(Page page) {
    this.page = page;
  }

  public Sort getSort() {
    return sort;
  }

  public void setSort(Sort sort) {
    this.sort = sort;
  }

  public Query withFilter(Filter filter) {
    this.filter = filter;
    return this;
  }

  public Query withPage(Page page) {
    this.page = page;
    return this;
  }

  public Query withSort(Sort sort) {
    this.sort = sort;
    return this;
  }
}
