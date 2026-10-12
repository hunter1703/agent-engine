package com.agentengine.util.models.config;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.genai.types.Modality;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultModels extends BaseEntity {

  public static final String ID = "default";
  public static final String SMART_ID = "default-smart";
  public static final String VISION_ID = "default-vision";
  public static final String FAST_ID = "default-fast";
  public static final String TEXT_EMBEDDING_ID = "default-text-embedding";
  public static final String IMAGE_EMBEDDING_ID = "default-image-embedding";

  private String smartModelId;
  private String visionModelId;
  private String fastModelId;
  private Map<String, String> modalityVsEmbeddingModelId = new LinkedHashMap<>();

  public DefaultModels() {
    super(ID);
  }

  public String getSmartModelId() {
    return smartModelId;
  }

  public void setSmartModelId(final String smartModelId) {
    this.smartModelId = smartModelId;
  }

  public String getVisionModelId() {
    return visionModelId;
  }

  public void setVisionModelId(final String visionModelId) {
    this.visionModelId = visionModelId;
  }

  public String getFastModelId() {
    return fastModelId;
  }

  public void setFastModelId(final String fastModelId) {
    this.fastModelId = fastModelId;
  }

  public Map<String, String> getModalityVsEmbeddingModelId() {
    return modalityVsEmbeddingModelId;
  }

  public void setModalityVsEmbeddingModelId(final Map<String, String> modalityVsEmbeddingModelId) {
    this.modalityVsEmbeddingModelId = CollectionUtils.nullSafeMap(modalityVsEmbeddingModelId);
  }

  public String getEmbeddingModelId(final Modality.Known modality) {
    return modality == null ? null : modalityVsEmbeddingModelId.get(modality.name());
  }

  public void setEmbeddingModelId(final Modality.Known modality, final String modelId) {
    if (modality != null) {
      modalityVsEmbeddingModelId.put(modality.name(), modelId);
    }
  }
}
