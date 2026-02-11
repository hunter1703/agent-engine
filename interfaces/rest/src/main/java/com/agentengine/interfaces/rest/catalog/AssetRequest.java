package com.agentengine.interfaces.rest.catalog;

import com.agentengine.engine.utils.Query;

import java.util.List;

public class AssetRequest {

  private String assetType;
  private List<String> keys;
  private Query query;

  public AssetRequest() {
  }

  public AssetRequest(String assetType, List<String> keys, Query query) {
    this.assetType = assetType;
    this.keys = keys;
    this.query = query;
  }

  public String getAssetType() {
    return assetType;
  }

  public void setAssetType(String assetType) {
    this.assetType = assetType;
  }

  public List<String> getKeys() {
    return keys;
  }

  public void setKeys(List<String> keys) {
    this.keys = keys;
  }

  public Query getQuery() {
    return query;
  }

  public void setQuery(final Query query) {
    this.query = query;
  }
}