package com.agentengine.util.common.query;

import com.agentengine.util.common.CollectionUtils;
import java.util.ArrayList;
import java.util.List;

public class Query {
    private Filter filter;
    private Page page;
    private Sort sort;
    private List<String> includeFields = new ArrayList<>();
    private List<String> excludeFields = new ArrayList<>();
    private boolean includeCount = false;

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

    public List<String> getIncludeFields() {
        return includeFields;
    }

    public void setIncludeFields(final List<String> includeFields) {
        this.includeFields = includeFields == null ? new ArrayList<>() : new ArrayList<>(includeFields);
    }

    public List<String> getExcludeFields() {
        return excludeFields;
    }

    public void setExcludeFields(final List<String> excludeFields) {
        this.excludeFields = excludeFields == null ? new ArrayList<>() : new ArrayList<>(excludeFields);
    }

    public Query withIncludeFields(final List<String> includeFields) {
        setIncludeFields(includeFields);
        return this;
    }

    public Query addIncludeField(final String includeField) {
        final List<String> updatedFields = CollectionUtils.nullSafeMutableList(includeFields);
        updatedFields.add(includeField);
        setIncludeFields(updatedFields);
        return this;
    }

    public Query withExcludeFields(final List<String> excludeFields) {
        setExcludeFields(excludeFields);
        return this;
    }

    public boolean isIncludeCount() {
        return includeCount;
    }

    public void setIncludeCount(final boolean includeCount) {
        this.includeCount = includeCount;
    }
}
