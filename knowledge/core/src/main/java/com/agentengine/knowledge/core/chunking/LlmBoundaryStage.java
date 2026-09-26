package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.Batcher;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LumberChunker-style LLM boundary detection (Duarte et al., 2024).
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Input chunks are treated as paragraphs, each assigned an incremental ID.
 *   <li>A sliding window {@code G_i} is built by appending chunks until the window exceeds {@link
 *       #WINDOW_TOKEN_BUDGET} estimated tokens (~2000, giving ~5 paragraphs per window).
 *   <li>The LLM is asked: "which paragraph ID is where the content first shifts significantly?" It
 *       returns a single {@code Answer: ID XXXX}.
 *   <li>All chunks before that ID are merged into one chunk; the identified chunk starts the next
 *       window. The process repeats until the document is exhausted.
 * </ol>
 *
 * <p>The LLM only outputs a single paragraph ID — no text is reproduced, so there is zero risk of
 * content mutation and output tokens are minimal.
 *
 * <p>Falls back to returning the original chunks unchanged if the model is unavailable or the
 * response cannot be parsed.
 *
 * <p>Chunks are folded into the current window one at a time as they arrive — like {@link
 * CosineBoundaryStage}, the whole input never needs to be materialized, since a window is only ever
 * as large as its own token budget regardless of how much of the document lies beyond it. An image
 * chunk (see {@link ChunkUtils#isMedia}) is never included in a window sent to the model — it
 * forces whatever window is pending to resolve immediately, passes through unchanged, and the
 * sliding window resumes fresh on whatever text chunks follow it.
 *
 * <p>When a found boundary doesn't consume the whole window, the leftover past it seeds the next
 * window rather than being discarded — the LumberChunker algorithm's boundary chunk becomes the
 * first chunk of the next one.
 */
public final class LlmBoundaryStage extends ChunkingStage {

  private static final Logger LOG = LoggerFactory.getLogger(LlmBoundaryStage.class);

  public static final int WINDOW_TOKEN_BUDGET = 2000;

  private static final Pattern ANSWER_PATTERN =
      Pattern.compile("Answer:\\s*ID\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

  private static final String SYSTEM_INSTRUCTIONS =
      """
                You are a document chunker that is expert in understanding nuances of text.
                You can understand the meaning of texts and can determine topics covered in any given text.

                You will receive a document with paragraphs identified by 'ID XXXX: <text>'
                Task: Find the first paragraph (not the very first one) where the content clearly changes compared to the previous paragraphs.

                Output: Return ONLY the ID of the paragraph where the content shift begins, in exactly this format: Answer: ID XXXX

                Additional considerations: Avoid very long groups of paragraphs. Aim for a good balance between identifying content shifts and keeping groups manageable.
                """;

  private final int approxCharsPerToken;
  private final String chatModelId;
  private final ModelProvider modelProvider;

  public LlmBoundaryStage(
      final int approxCharsPerToken, final String chatModelId, final ModelProvider modelProvider) {
    this.approxCharsPerToken = approxCharsPerToken;
    this.chatModelId = chatModelId;
    this.modelProvider = modelProvider;
  }

  @Override
  public Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    final List<KnowledgeChunk> results = new ArrayList<>();
    final WindowBatcher batcher = new WindowBatcher(results::addAll);
    return chunks
        .concatMap(
            chunk -> {
              results.clear();
              if (ChunkUtils.isMedia(chunk)) {
                batcher.flush();
                results.add(chunk);
              } else {
                batcher.add(chunk);
              }
              return Flowable.fromIterable(List.copyOf(results));
            })
        .concatWith(
            Flowable.defer(
                () -> {
                  results.clear();
                  batcher.flush();
                  return Flowable.fromIterable(List.copyOf(results));
                }));
  }

  /**
   * Builds the numbered window and asks the LLM for the boundary paragraph's index within it (not
   * an absolute document position — the window is all this stage ever sees at once). Returns the
   * 0-based index of the boundary paragraph, or {@code -1} on failure.
   */
  private int askForBoundary(final List<KnowledgeChunk> window) {
    final StringBuilder doc = new StringBuilder();
    for (int i = 0; i < window.size(); i++) {
      final String text = window.get(i).getText();
      doc.append("ID %04d: %s%n".formatted(i, text != null ? text : ""));
    }

    final String prompt = "Document:\n" + doc;

    try {
      final List<Content> contents = List.of(Content.fromParts(Part.fromText(prompt)));
      final LlmRequest request =
          LlmRequest.builder()
              .appendInstructions(List.of(SYSTEM_INSTRUCTIONS))
              .contents(contents)
              .build();

      final String responseText;
      try (RefCounted<Model.LLMModel> refCounted = modelProvider.get(chatModelId)) {
        responseText =
            refCounted
                .value()
                .model()
                .generateContent(request, false)
                .map(response -> response.content().map(Content::text).orElseThrow())
                .blockingSingle();
      }
      final Matcher matcher = ANSWER_PATTERN.matcher(responseText);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
      LOG.warn("LlmBoundaryStage: could not parse boundary ID from response: {}", responseText);
    } catch (final Exception e) {
      LOG.warn("LlmBoundaryStage: LLM call failed. Cause: {}", e.getMessage());
    }
    return -1;
  }

  /**
   * Flushes once the window (which already includes the chunk just added) reaches {@link
   * #WINDOW_TOKEN_BUDGET}. Overrides {@link #flush} (rather than just {@link #shouldFlush}) because
   * a window doesn't always flush whole: the LLM's boundary can land mid-window, and the chunks
   * past it need to seed the next window rather than being discarded (see the class doc) — {@link
   * Batcher}'s default flush-the-whole-buffer behavior doesn't leave room for that. Loops until the
   * buffer is fully drained rather than resolving one boundary and stopping, so a final flush at
   * the end of the stream (or before an image chunk) doesn't strand a mid-window remainder
   * unresolved — nothing would call {@link #flush} again to pick it up.
   */
  private final class WindowBatcher extends Batcher<KnowledgeChunk> {

    WindowBatcher(final Consumer<List<KnowledgeChunk>> onFlush) {
      super(onFlush);
    }

    @Override
    protected boolean shouldFlush() {
      return buffer.stream().mapToInt(ChunkUtils::textLength).sum()
          >= WINDOW_TOKEN_BUDGET * approxCharsPerToken;
    }

    @Override
    public void flush() {
      while (!buffer.isEmpty()) {
        final int boundaryId = buffer.size() <= 1 ? -1 : askForBoundary(buffer);
        final boolean foundBoundary = boundaryId > 0 && boundaryId < buffer.size();
        final KnowledgeChunk merged =
            ChunkUtils.mergeTexts(foundBoundary ? buffer.subList(0, boundaryId) : buffer);
        final List<KnowledgeChunk> remainder =
            foundBoundary ? new ArrayList<>(buffer.subList(boundaryId, buffer.size())) : List.of();
        buffer.clear();
        buffer.addAll(remainder);
        onFlush.accept(List.of(merged));
      }
    }
  }
}
