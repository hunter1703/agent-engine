package com.agentengine.engine.api.query;

import com.agentengine.engine.api.utils.Page;

public class Query {
  private Filter filter;
  private Page page;

  public Query() {
  }

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

  public Query withFilter(Filter filter) {
    this.filter = filter;
    return this;
  }

  public Query withPage(Page page) {
    this.page = page;
    return this;
  }
}
