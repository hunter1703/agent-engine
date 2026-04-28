package com.agentengine.knowledge.core.chunking;

import com.agentengine.agent.infra.factories.model.EmbeddingModelFactory;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts chunk boundaries where the cosine similarity between adjacent sentence embeddings drops
 * below a threshold. Merges sentences into coherent chunks.
 */
public final class CosineBoundaryStage extends ChunkingStage {

    private final int maxSegmentSize;
    private final double similarityThreshold;
    private final String embeddingModelId;
    private final EmbeddingModelFactory embeddingModelFactory;

    public CosineBoundaryStage(
            final int maxSegmentSize,
            final double similarityThreshold,
            final String embeddingModelId,
            final EmbeddingModelFactory embeddingModelFactory) {
        this.maxSegmentSize = maxSegmentSize;
        this.similarityThreshold = similarityThreshold;
        this.embeddingModelId = embeddingModelId;
        this.embeddingModelFactory = embeddingModelFactory;
    }

    @Override
    public List<KnowledgeChunk> apply(final List<KnowledgeChunk> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        final EmbeddingModel embeddingModel = embeddingModelFactory.get(embeddingModelId);
        try {
            final float[][] vectors = chunks.stream()
                    .map(chunk ->
                            embeddingModel.embed(chunk.getText()).content().vector())
                    .toArray(float[][]::new);

            final double[] similarities = new double[vectors.length - 1];
            for (int i = 0; i < similarities.length; i++) {
                similarities[i] = cosine(vectors[i], vectors[i + 1]);
            }

            final List<KnowledgeChunk> result = new ArrayList<>();
            final List<KnowledgeChunk> chunkBatch = new ArrayList<>();
            int currentChars = 0;
            int globalIndex = 0;

            for (int i = 0; i < chunks.size(); i++) {
                final KnowledgeChunk chunk = chunks.get(i);
                final boolean cut = i < similarities.length && similarities[i] < similarityThreshold;
                final boolean tooLarge =
                        currentChars + chunk.getText().length() > maxSegmentSize && !chunkBatch.isEmpty();

                if ((cut || tooLarge) && !chunkBatch.isEmpty()) {
                    result.add(ChunkUtils.mergeTexts(chunkBatch, globalIndex++));
                    chunkBatch.clear();
                    currentChars = 0;
                }
                chunkBatch.add(chunk);
                currentChars += chunk.getText().length();
            }
            if (!chunkBatch.isEmpty()) {
                result.add(ChunkUtils.mergeTexts(chunkBatch, globalIndex));
            }
            return result;
        } finally {
            embeddingModelFactory.release(embeddingModelId);
        }
    }

    private static double cosine(final float[] a, final float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        final double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
