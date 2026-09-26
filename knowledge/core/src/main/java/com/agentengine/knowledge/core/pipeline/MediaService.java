package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;

/**
 * Turns media bytes into searchable text — shared by any indexer that needs to describe media,
 * whether it's the whole knowledge (see {@link ImageKnowledgeIndexer}) or embedded within another
 * document (see {@link PdfKnowledgeIndexer}).
 */
public interface MediaService {

  /** Describes an image with the configured vision model, transcribing any visible text too. */
  ImageDescription describeImage(Knowledge knowledge, byte[] imageBytes);

  /** {@code ocrText} is empty, not null, when the image has no visible text. */
  record ImageDescription(String description, String ocrText) {}
}
