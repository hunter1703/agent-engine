package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;

import java.util.Base64;

public class Page {
  private int offset;
  private int limit;
  private String cursor;

  public Page() {
    this(0, 20);
  }

  public Page(int offset, int limit) {
    this.offset = offset;
    this.limit = limit;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(final int offset) {
    this.offset = offset;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(final int limit) {
    this.limit = limit;
  }

  public String getCursor() {
    return cursor;
  }

  public void setCursor(final String cursor) {
    this.cursor = cursor;
  }

  public static Page sanitize(Page page) {
    if (page == null) {
      return null;
    }
    if (StringUtils.isBlank(page.getCursor())) {
      return page;
    }
    final String jsonPage = new String(Base64.getDecoder().decode(page.getCursor()));
    return JsonUtils.fromJson(jsonPage, Page.class);
  }
}
