package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.common.StringUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import java.util.ArrayList;
import java.util.List;

/**
 * Caps each input chunk to a maximum estimated token count (approximated as chars / 3). Oversized
 * chunks are split at sentence boundaries.
 */
public final class MaxTokenCapStage extends ChunkingStage {

  private final int approxCharsPerToken;
  private final int maxTokensPerSegment;

  public MaxTokenCapStage(final int approxCharsPerToken, final int maxTokensPerSegment) {
    this.approxCharsPerToken = approxCharsPerToken;
    this.maxTokensPerSegment = maxTokensPerSegment;
  }

  @Override
  public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
    final int maxChars = maxTokensPerSegment * approxCharsPerToken;
    return ChunkUtils.splitChunks(
        chunks,
        text -> {
          if (text.length() <= maxChars) {
            return List.of(text);
          }
          return splitBySentence(text, maxChars);
        });
  }

  private static List<String> splitBySentence(final String text, final int maxChars) {
    final DocumentBySentenceSplitter splitter =
        new DocumentBySentenceSplitter(Integer.MAX_VALUE, 0);
    final List<String> sentences =
        splitter.split(Document.from(text)).stream()
            .map(seg -> seg.text().trim())
            .filter(StringUtils::isNotBlank)
            .toList();

    final List<String> result = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    for (final String sentence : sentences) {
      if (!current.isEmpty() && current.length() + 1 + sentence.length() > maxChars) {
        result.add(current.toString().trim());
        current.setLength(0);
      }
      if (!current.isEmpty()) {
        current.append(' ');
      }
      current.append(sentence);
    }
    if (!current.isEmpty()) {
      result.add(current.toString().trim());
    }
    return result;
  }
}
