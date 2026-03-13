package com.agentengine.util.common.query;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

public class PaginatedResult<T> {
  private List<T> items;
  private String nextCursor;
  private Long total;
  private boolean hasMore;

  public List<T> getItems() {
    return items;
  }

  public void setItems(final List<T> items) {
    this.items = items;
  }

  public String getNextCursor() {
    return nextCursor;
  }

  public void setNextCursor(final String nextCursor) {
    this.nextCursor = nextCursor;
  }

  public Long getTotal() {
    return total;
  }

  public void setTotal(final Long total) {
    this.total = total;
  }

  public boolean isHasMore() {
    return hasMore;
  }

  public void setHasMore(final boolean hasMore) {
    this.hasMore = hasMore;
  }

  public static <S> PaginatedResult<S> create(List<S> items, Page page, Long total) {
    PaginatedResult<S> result = new PaginatedResult<>();
    result.setItems(items);
    result.setTotal(total);
    boolean hasMore = false;
    if (total != null) {
      hasMore = page.getOffset() + page.getLimit() < total;
    } else {
      hasMore = items.size() == page.getLimit();
    }
    result.setHasMore(hasMore);
    if (result.isHasMore()) {
      Page nextPage = new Page(page.getOffset() + page.getLimit(), page.getLimit());
      result.setNextCursor(Base64.getEncoder().encodeToString(JsonUtils.toJson(nextPage).getBytes()));
    }
    return result;
  }

  public static <S> PaginatedResult<S> create(final List<S> items) {
    final PaginatedResult<S> result = new PaginatedResult<>();
    result.setItems(items);
    result.setTotal(null);
    result.setHasMore(false);
    result.setNextCursor(null);
    return result;
  }

  public <S> PaginatedResult<S> transform(Function<T, S> transformer) {
    PaginatedResult<S> result = new PaginatedResult<>();
    result.setItems(CollectionUtils.nullSafeList(this.items).stream().map(transformer).toList());
    result.setNextCursor(this.nextCursor);
    result.setTotal(this.total);
    result.setHasMore(this.hasMore);
    return result;
  }
}
