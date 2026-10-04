package com.agentengine.knowledge.core.services;

import com.agentengine.knowledge.api.beans.*;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.knowledge.core.pipeline.KnowledgeIndexer;
import com.agentengine.knowledge.core.repository.KnowledgeRepository;
import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.*;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.tenancy.Permission;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class KnowledgeServiceImpl implements KnowledgeService {

  private static final Logger LOG = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

  /**
   * Not a compile dependency on {@code knowledge:jobs} — the scheduler loads the job class
   * reflectively at fire time (see {@code JobRunnerActor}), so only its name is needed here.
   */
  private static final String KNOWLEDGE_INDEXING_JOB_CLASS_NAME =
      "com.agentengine.knowledge.jobs.KnowledgeIndexingJob";

  private static final String KNOWLEDGE_ID_KEY = "knowledgeId";

  private final KnowledgeRepository knowledgeRepo;
  private final LazyLoader<List<KnowledgeIndexer>> indexers;
  private final SchedulerService schedulerService;

  @Inject
  public KnowledgeServiceImpl(
      final KnowledgeRepository knowledgeRepo,
      final Instance<KnowledgeIndexer> indexers,
      final SchedulerService schedulerService) {
    this.knowledgeRepo = knowledgeRepo;
    this.indexers =
        new LazyLoader<>(
            () -> {
              final List<KnowledgeIndexer> sorted =
                  indexers.stream()
                      .sorted(Comparator.comparingInt(KnowledgeIndexer::priority))
                      .toList();
              LOG.info(
                  "KnowledgeServiceImpl initialized with {} indexers: {}",
                  sorted.size(),
                  sorted.stream().map(i -> i.getClass().getSimpleName()).toList());
              return sorted;
            });
    this.schedulerService = schedulerService;
  }

  @Override
  public Map<String, Acl> getAcls(final String assetClass, final Collection<String> assetIds) {
    return knowledgeRepo.readAcls(assetIds);
  }

  @Override
  public Set<String> applyAcls(final String assetClass, final Map<String, Acl> assetIdVsAcl) {
    return knowledgeRepo.applyAcls(assetIdVsAcl);
  }

  @Override
  public Knowledge create(final IndexRequest request) {
    final Knowledge knowledge = new Knowledge();
    init(knowledge, request);
    if (request.isSkipIndexing()) {
      knowledge.setIndexingStatus(IndexingStatus.NON_INDEXED.name());
      knowledgeRepo.insert(knowledge);
      return knowledge;
    }
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
    return knowledgeRepo.deleteByIdIgnoringVersion(id);
  }

  @Override
  public Set<String> findPermittedIds(final Collection<String> ids, final Permission permission) {
    return knowledgeRepo.findPermittedIds(ids, permission);
  }

  @Override
  public PaginatedResult<KnowledgeChunk> searchInKnowledge(
      final Collection<String> knowledgeIds, final Query query) {
    return knowledgeRepo.findChunks(knowledgeIds, query);
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

  /**
   * Indexes the knowledge as read when indexing was requested. Each status write is versioned on
   * the one before it, so a run overtaken by a change to the knowledge — a re-upload, a deletion —
   * stops instead of overwriting what the change wrote.
   */
  private void runIndexing(final Knowledge knowledge) {
    final String id = knowledge.getId();
    final Knowledge inProgress;
    try {
      inProgress = knowledgeRepo.update(knowledge, statusUpdate(IndexingStatus.IN_PROGRESS, null));
    } catch (final StaleStateException exception) {
      LOG.info("Knowledge {} changed since indexing was requested; skipping this run", id);
      return;
    }
    try {

      final List<KnowledgeIndexer> availableIndexers = indexers.get();
      LOG.debug(
          "Looking for indexer for knowledge {} among {} available indexers",
          id,
          availableIndexers.size());
      final KnowledgeIndexer indexer =
          availableIndexers.stream()
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
      final KnowledgeIndexer.IndexResult result = indexer.index(inProgress);

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
      knowledgeRepo.update(inProgress, new Update(operations));
      knowledgeRepo.updateChunkStatus(id, inProgress.getVersion(), IndexingStatus.COMPLETED.name());
      knowledgeRepo.deleteOlderVersions(id, inProgress.getVersion());
      LOG.info("Indexed knowledge {} — {} chunks", id, result.chunkCount());
    } catch (final StaleStateException exception) {
      LOG.info("Knowledge {} changed while indexing; result dropped", id);
      knowledgeRepo.deleteChunksOfVersion(id, inProgress.getVersion());
    } catch (final Exception e) {
      LOG.error("Indexing failed for knowledge {}", id, e);
      try {
        knowledgeRepo.update(inProgress, statusUpdate(IndexingStatus.FAILED, e.getMessage()));
      } catch (final StaleStateException exception) {
        LOG.info("Knowledge {} changed while indexing; failure not recorded", id);
      }
      knowledgeRepo.deleteChunksOfVersion(id, inProgress.getVersion());
    }
  }

  private static Update statusUpdate(final IndexingStatus status, final String error) {
    return Update.of(
        Operation.set(Knowledge.FIELD_INDEXING_STATUS, status.name()),
        Operation.set(Knowledge.FIELD_ERROR, error));
  }

  private static void init(final Knowledge knowledge, final IndexRequest request) {
    knowledge.setAgentId(request.getAgentId());
    knowledge.setFileDetails(request.getFileDetails());
    knowledge.setTitle(request.getTitle());
    knowledge.setDescription(request.getDescription());
    knowledge.setSettings(request.getSettings());
    knowledge.setTotalChunks(0);
    knowledge.setIndexingStatus(IndexingStatus.PENDING.name());
    knowledge.setError(null);
  }
}
