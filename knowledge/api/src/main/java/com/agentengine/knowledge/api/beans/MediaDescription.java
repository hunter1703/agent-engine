package com.agentengine.knowledge.api.beans;

/**
 * Extracted description and transcript for a media file (images, audio, video).
 *
 * @param description Semantic caption or visual/auditory description of the media.
 * @param transcript Extracted text, such as OCR text for images or speech transcript for audio.
 */
public record MediaDescription(String description, String transcript) {

  public MediaDescription {
    description = description == null ? "" : description;
    transcript = transcript == null ? "" : transcript;
  }
}
