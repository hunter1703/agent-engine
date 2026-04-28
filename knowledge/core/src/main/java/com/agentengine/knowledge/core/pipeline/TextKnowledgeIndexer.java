package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.chunking.ChunkingPipeline;
import com.agentengine.knowledge.core.chunking.ChunkingPipelineFactory;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.repository.Repository;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indexes plain-text knowledge by reading the file content, running it through the configured
 * {@link ChunkingPipeline}, and persisting the resulting {@link KnowledgeChunk}s (with vectors
 * already populated by the terminal {@code EmbeddingStage}) to the vector store.
 */
public class TextKnowledgeIndexer implements KnowledgeIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(TextKnowledgeIndexer.class);

    private final ChunkingPipelineFactory chunkingPipelineFactory;
    private final Repository<KnowledgeChunk> vectorStore;
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
            final String text = new String(content.readAllBytes(), StandardCharsets.UTF_8);
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

            // Assign stable IDs before persisting
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setId(knowledge.getId() + "-" + i);
                chunks.get(i).setChunkIndex(i);
            }

            chunks.forEach(vectorStore::save);
            LOG.info("Indexed {} chunks for knowledge {}", chunks.size(), knowledge.getId());
            return chunks.size();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to index knowledge " + knowledge.getId(), e);
        }
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
