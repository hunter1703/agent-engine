package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingPipeline;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.cloudstorage.FileService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.*;

/**
 * Indexes plain-text knowledge by reading the file content, running it through the configured
 * {@link ChunkingPipeline}, and persisting the resulting {@link KnowledgeChunk}s (with vectors
 * already populated by the terminal {@code EmbeddingStage}) to the vector store.
 */
@Singleton
public class TextKnowledgeIndexer implements KnowledgeIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(TextKnowledgeIndexer.class);

    private final ChunkingPipelineFactory chunkingPipelineFactory;
    private final KnowledgeChunkStore vectorStore;
    private final FileService fileService;

    @Inject
    public TextKnowledgeIndexer(
            final ChunkingPipelineFactory chunkingPipelineFactory,
            final KnowledgeChunkStore vectorStore,
            final FileService fileService) {
        this.chunkingPipelineFactory = chunkingPipelineFactory;
        this.vectorStore = vectorStore;
        this.fileService = fileService;
    }

    @Override
    public boolean canIndex(final Knowledge knowledge) {
        return true;
    }

    @Override
    public int index(final Knowledge knowledge) {
        try (final InputStream content = fileService.getContent(knowledge.getFileDetails())) {
            final String text = new String(content.readAllBytes(), UTF_8);
            final KnowledgeSettings settings = knowledge.getSettings();
            final ChunkingPipeline pipeline = chunkingPipelineFactory.create(settings);

            // Seed chunk carries the full document text and identity metadata.
            // Pipeline stages split, merge, and finally embed — producing ready-to-persist chunks.
            final KnowledgeChunk seed = new KnowledgeChunk();
            seed.setKnowledgeId(knowledge.getId());
            seed.setAgentId(knowledge.getAgentId());
            seed.setChunkIndex(0);
            seed.setText(text);
            seed.setChunkStart(0);
            seed.setChunkEnd(text.length());

            final List<KnowledgeChunk> chunks = pipeline.run(seed);

            // Assign stable UUIDs before persisting
            // Qdrant requires point IDs to be either unsigned integers or UUIDs
            for (int i = 0; i < chunks.size(); i++) {
                final String deterministicId = generateChunkId(knowledge.getId(), i);
                chunks.get(i).setId(deterministicId);
                chunks.get(i).setChunkIndex(i);
            }

            chunks.forEach(vectorStore::save);
            LOG.info("Indexed {} chunks for knowledge {}", chunks.size(), knowledge.getId());
            return chunks.size();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to index knowledge " + knowledge.getId(), e);
        }
    }

    /**
     * Generates a deterministic UUID for a knowledge chunk.
     * Uses UUID v5 (name-based with SHA-1) to create a stable, reproducible ID.
     */
    private static String generateChunkId(final String knowledgeId, final int chunkIndex) {
        final String name = knowledgeId + "-" + chunkIndex;
        return java.util
                .UUID
                .nameUUIDFromBytes(name.getBytes(UTF_8))
                .toString();
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
