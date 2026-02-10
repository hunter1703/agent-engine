package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import io.netty.handler.codec.base64.Base64Decoder;

import java.util.Base64;
import java.util.List;

public class PaginatedResult<T> {
    private List<T> items;
    private String nextCursor;
    private Integer total;
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

    public Integer getTotal() {
        return total;
    }

    public void setTotal(final Integer total) {
        this.total = total;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(final boolean hasMore) {
        this.hasMore = hasMore;
    }

    public static <S> PaginatedResult<S> create(List<S> items, Page page, int total) {
        PaginatedResult<S> result = new PaginatedResult<>();
        result.setItems(items);
        result.setTotal(total);
        result.setHasMore(page.getOffset() + page.getLimit() < total);
        if (result.isHasMore()) {
            Page nextPage = new Page(page.getOffset() + page.getLimit(), page.getLimit());
            result.setNextCursor(Base64.getEncoder().encodeToString(JsonUtils.toJson(nextPage).getBytes()));
        }
        return result;
    }

    public static <S> PaginatedResult<S> create(List<S> items, Page page) {
        PaginatedResult<S> result = new PaginatedResult<>();
        result.setItems(items);
        final boolean hasMore = CollectionUtils.isNotEmpty(items) && items.size() >= page.getLimit();
        result.setHasMore(hasMore);
        if (hasMore) {
            Page nextPage = new Page(page.getOffset() + page.getLimit(), page.getLimit());
            result.setNextCursor(Base64.getEncoder().encodeToString(JsonUtils.toJson(nextPage).getBytes()));
        }
        return result;
    }
}
