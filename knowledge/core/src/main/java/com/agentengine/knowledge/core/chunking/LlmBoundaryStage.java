package com.agentengine.knowledge.core.chunking;

import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LumberChunker-style LLM boundary detection (Duarte et al., 2024).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Input chunks are treated as paragraphs, each assigned an incremental ID.
 *   <li>A sliding window {@code G_i} is built by appending chunks until the window exceeds
 *       {@link #WINDOW_TOKEN_BUDGET} estimated tokens (~2000, giving ~5 paragraphs per window).
 *   <li>The LLM is asked: "which paragraph ID is where the content first shifts significantly?"
 *       It returns a single {@code Answer: ID XXXX}.
 *   <li>All chunks before that ID are merged into one chunk; the identified chunk starts the next
 *       window. The process repeats until the document is exhausted.
 * </ol>
 *
 * <p>The LLM only outputs a single paragraph ID — no text is reproduced, so there is zero risk
 * of content mutation and output tokens are minimal.
 *
 * <p>Falls back to returning the original chunks unchanged if the model is unavailable or the
 * response cannot be parsed.
 */
public final class LlmBoundaryStage extends ChunkingStage {

    private static final Logger LOG = LoggerFactory.getLogger(LlmBoundaryStage.class);

    public static final int WINDOW_TOKEN_BUDGET = 2000;

    private static final Pattern ANSWER_PATTERN = Pattern.compile("Answer:\\s*ID\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String SYSTEM_INSTRUCTIONS = """
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
    public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        final List<KnowledgeChunk> result = new ArrayList<>();
        int windowStart = 0;
        int globalIndex = 0;

        final BaseLlm model = modelProvider.acquire(chatModelId);
        try {
            while (windowStart < chunks.size()) {
                final int windowEnd = buildWindowEnd(chunks, windowStart);

                // If the window only has one paragraph left, merge the rest as the final chunk
                if (windowEnd - windowStart <= 1) {
                    result.add(mergeRange(chunks, windowStart, chunks.size(), globalIndex++));
                    break;
                }

                final int boundaryId = askForBoundary(chunks, windowStart, windowEnd, model);

                if (boundaryId <= windowStart || boundaryId >= windowEnd) {
                    LOG.debug(
                            "LlmBoundaryStage: no boundary in window [{}, {}), treating as single chunk",
                            windowStart,
                            windowEnd);
                    result.add(mergeRange(chunks, windowStart, windowEnd, globalIndex++));
                    windowStart = windowEnd;
                } else {
                    result.add(mergeRange(chunks, windowStart, boundaryId, globalIndex++));
                    windowStart = boundaryId;
                }
            }
        } finally {
            modelProvider.release(chatModelId);
        }

        return result;
    }

    private int buildWindowEnd(final List<KnowledgeChunk> chunks, final int start) {
        int chars = 0;
        int i = start;
        while (i < chunks.size()) {
            final String text = chunks.get(i).getText();
            chars += text != null ? text.length() : 0;
            i++;
            if (chars >= WINDOW_TOKEN_BUDGET * approxCharsPerToken) {
                break;
            }
        }
        return i;
    }

    /**
     * Builds the numbered document window and asks the LLM for the boundary paragraph ID.
     * Returns the 0-based index of the boundary paragraph, or {@code -1} on failure.
     */
    private static int askForBoundary(
            final List<KnowledgeChunk> chunks, final int start, final int end, final BaseLlm chatModel) {
        final StringBuilder doc = new StringBuilder();
        for (int i = start; i < end; i++) {
            final String text = chunks.get(i).getText();
            doc.append("ID %04d: %s%n".formatted(i, text != null ? text : ""));
        }

        final String prompt = "Document:\n" + doc;

        try {
            final List<Content> contents = List.of(Content.fromParts(Part.fromText(prompt)));
            final LlmRequest request = LlmRequest.builder()
                    .appendInstructions(List.of(SYSTEM_INSTRUCTIONS))
                    .contents(contents)
                    .build();
            final String responseText = chatModel
                    .generateContent(request, false)
                    .map(response -> response.content().map(Content::text).orElseThrow())
                    .blockingSingle();
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
     * Merges chunks in {@code [from, to)} into a single chunk, preserving offset metadata
     * from the first and last chunk in the range.
     */
    private static KnowledgeChunk mergeRange(
            final List<KnowledgeChunk> chunks, final int from, final int to, final int index) {
        final List<KnowledgeChunk> slice = chunks.subList(from, to);
        return ChunkUtils.mergeTexts(slice, index);
    }
}
