package com.agentengine.knowledge.core.services;

import com.agentengine.knowledge.api.beans.*;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.core.pipeline.KnowledgeIndexer;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.knowledge.core.store.KnowledgeChunkStore;
import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.repository.Repository;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class KnowledgeServiceImpl implements KnowledgeService {

  private static final Logger LOG = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

  /**
   * Not a compile dependency on {@code knowledge:jobs} — the scheduler loads the job class
   * reflectively at fire time (see {@code JobRunnerActor}), so only its name is needed here, the
   * same way {@code InvokeAgentJobAssetHandler} names {@code InvokeAgentJob}.
   */
  private static final String KNOWLEDGE_INDEXING_JOB_CLASS_NAME =
      "com.agentengine.knowledge.jobs.KnowledgeIndexingJob";

  private static final String KNOWLEDGE_ID_KEY = "knowledgeId";

  private final KnowledgeRepository knowledgeRepo;
  private final List<KnowledgeIndexer> indexers;
  private final Repository<KnowledgeChunk> vectorStore;
  private final SchedulerService schedulerService;

  @Inject
  public KnowledgeServiceImpl(
      final KnowledgeRepository knowledgeRepo,
      final Instance<KnowledgeIndexer> indexers,
      final KnowledgeChunkStore vectorStore,
      final SchedulerService schedulerService) {
    this.knowledgeRepo = knowledgeRepo;
    this.indexers =
        indexers.stream().sorted(Comparator.comparingInt(KnowledgeIndexer::priority)).toList();
    this.vectorStore = vectorStore;
    this.schedulerService = schedulerService;
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
  public Map<String, Knowledge> findByIds(Collection<String> id) {
    return knowledgeRepo.findByIds(id);
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
  public void runIndexing(final String knowledgeId) {
    final Knowledge knowledge = knowledgeRepo.findById(knowledgeId);
    if (knowledge == null) {
      LOG.warn("Knowledge {} not found; skipping indexing", knowledgeId);
      return;
    }
    runIndexing(knowledge);
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

  /**
   * Runs the indexing pipeline on the scheduler's own workers rather than this pod's, via a one-off
   * {@link JobDefinition} that fires immediately (see {@link KnowledgeService#runIndexing}).
   */
  private void scheduleIndexing(final Knowledge knowledge) {
    final JobDefinition jobDefinition = new JobDefinition();
    jobDefinition.setJobClassName(KNOWLEDGE_INDEXING_JOB_CLASS_NAME);
    jobDefinition.setRunAt(System.currentTimeMillis());
    jobDefinition.setPayload(Map.of(KNOWLEDGE_ID_KEY, knowledge.getId()));
    schedulerService.schedule(jobDefinition);
  }

  private void runIndexing(final Knowledge knowledge) {
    final String id = knowledge.getId();
    try {
      deleteChunks(id);
      markStatus(id, IndexingStatus.IN_PROGRESS, null);

      LOG.debug(
          "Looking for indexer for knowledge {} among {} available indexers", id, indexers.size());
      final KnowledgeIndexer indexer =
          indexers.stream()
              .filter(
                  knowledgeIndexer -> {
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
      final KnowledgeIndexer.IndexResult result = indexer.index(knowledge);

      final List<Operation> operations =
          new ArrayList<>(
              List.of(
                  Operation.set(Knowledge.FIELD_INDEXING_STATUS, IndexingStatus.COMPLETED.name()),
                  Operation.set(Knowledge.FIELD_INDEXED_AT, System.currentTimeMillis()),
                  Operation.set(Knowledge.FIELD_TOTAL_CHUNKS, result.chunkCount()),
                  Operation.set(Knowledge.FIELD_CONTENT_PREVIEW, result.contentPreview())));
      if (result.generatedDescription() != null) {
        operations.add(Operation.set(Knowledge.FIELD_DESCRIPTION, result.generatedDescription()));
      }
      knowledgeRepo.update(id, new Update(operations));
      LOG.info("Indexed knowledge {} — {} chunks", id, result.chunkCount());
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
