package com.agentengine.knowledge.core.services;

import com.agentengine.knowledge.api.beans.*;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.core.pipeline.KnowledgeIndexer;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.repository.Repository;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final KnowledgeRepository knowledgeRepo;
    private final List<KnowledgeIndexer> indexers;
    private final Repository<KnowledgeChunk> vectorStore;

    @Inject
    public KnowledgeServiceImpl(
            final KnowledgeRepository knowledgeRepo,
            final Instance<KnowledgeIndexer> indexers,
            final KnowledgeChunkStore vectorStore) {
        this.knowledgeRepo = knowledgeRepo;
        this.indexers = indexers.stream()
                .sorted(Comparator.comparingInt(KnowledgeIndexer::priority))
                .toList();
        this.vectorStore = vectorStore;
        LOG.info(
                "KnowledgeServiceImpl initialized with {} indexers: {}",
                this.indexers.size(),
                this.indexers.stream().map(i -> i.getClass().getSimpleName()).toList());
    }

    @Override
    public Knowledge create(final IndexRequest request) {
        final Knowledge knowledge = new Knowledge();
        init(knowledge, request);
        knowledgeRepo.insert(knowledge);
        if (request.isWaitForCompletion()) {
            runIndexing(knowledge);
        } else {
            scheduleIndexing(knowledge);
        }
        return knowledge;
    }

    @Override
    public Knowledge findById(final String id) {
        return knowledgeRepo.findById(id);
    }

    @Override
    public PaginatedResult<Knowledge> findByQuery(final Query query) {
        return knowledgeRepo.findByQuery(query);
    }

    @Override
    public Knowledge reindex(final String id, final IndexRequest request) {
        final Knowledge existing = knowledgeRepo.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Knowledge not found: " + id);
        }
        if (!Objects.equals(request.getAgentId(), existing.getAgentId())) {
            throw new IllegalArgumentException("Agent id must match");
        }
        init(existing, request);
        knowledgeRepo.update(id, existing);
        scheduleIndexing(existing);
        return existing;
    }

    @Override
    public boolean deleteById(final String id) {
        final boolean deleted = knowledgeRepo.deleteById(id);
        if (deleted) {
            deleteChunks(id);
        }
        return deleted;
    }

    @Override
    public PaginatedResult<KnowledgeChunk> searchInKnowledge(Query query) {
        return vectorStore.findByQuery(query);
    }

    private void scheduleIndexing(final Knowledge knowledge) {
        Thread.ofVirtual().start(() -> runIndexing(knowledge));
    }

    private void runIndexing(final Knowledge knowledge) {
        final String id = knowledge.getId();
        try {
            // Delete old vectors before re-indexing
            deleteChunks(id);
            markStatus(id, IndexingStatus.IN_PROGRESS, null);

            LOG.info("Looking for indexer for knowledge {} among {} available indexers", id, indexers.size());
            final KnowledgeIndexer indexer = indexers.stream()
                    .filter(knowledgeIndexer -> {
                        final boolean canIndex = knowledgeIndexer.canIndex(knowledge);
                        LOG.debug(
                                "Indexer {} canIndex={}",
                                knowledgeIndexer.getClass().getSimpleName(),
                                canIndex);
                        return canIndex;
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No suitable indexer found"));
            LOG.info("Selected indexer: {}", indexer.getClass().getSimpleName());
            final int chunks = indexer.index(knowledge);

            knowledgeRepo.update(
                    id,
                    Update.of(
                            Operation.set(Knowledge.FIELD_INDEXING_STATUS, IndexingStatus.COMPLETED.name()),
                            Operation.set(Knowledge.FIELD_INDEXED_AT, System.currentTimeMillis()),
                            Operation.set(Knowledge.FIELD_TOTAL_CHUNKS, chunks)));
            LOG.info("Indexed knowledge {} — {} chunks", id, chunks);
        } catch (Exception e) {
            LOG.error("Indexing failed for knowledge {}", id, e);
            markStatus(id, IndexingStatus.FAILED, e.getMessage());
        }
    }

    private void markStatus(final String id, final IndexingStatus status, final String error) {
        final Knowledge knowledge = knowledgeRepo.findById(id);
        if (knowledge == null) {
            return;
        }
        knowledgeRepo.update(
                id,
                Update.of(
                        Operation.set(Knowledge.FIELD_INDEXING_STATUS, status.name()),
                        Operation.set(Knowledge.FIELD_ERROR, error)));
    }

    private void deleteChunks(final String id) {
        vectorStore.deleteByQuery(new Query().withFilter(Filters.eq("knowledgeId", id)));
    }

    private static void init(final Knowledge knowledge, final IndexRequest request) {
        knowledge.setAgentId(request.getAgentId());
        knowledge.setGrants(request.getGrants());
        knowledge.setFileDetails(request.getFileDetails());
        knowledge.setTitle(request.getTitle());
        knowledge.setDescription(request.getDescription());
        knowledge.setSettings(request.getSettings());
        knowledge.setTotalChunks(0);
        knowledge.setIndexingStatus(IndexingStatus.PENDING.name());
        knowledge.setError(null);
    }
}
