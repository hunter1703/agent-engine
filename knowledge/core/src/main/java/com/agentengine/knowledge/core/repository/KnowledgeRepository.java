package com.agentengine.knowledge.core.repository;

import com.agentengine.knowledge.api.beans.IndexingStatus;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.core.store.KnowledgeChunkRepository;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.context.Context;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;

@Singleton
@Startup
public class KnowledgeRepository extends AbstractPermissionedRepository<Knowledge> {

  private final KnowledgeChunkRepository chunkRepository;

  @Inject
  public KnowledgeRepository(
      final DocumentBackend documentBackend,
      final KnowledgeChunkRepository chunkRepository,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                KnowledgeDocumentStoreClientType.KNOWLEDGE, Knowledge.class)),
        validationService,
        permissionChecker,
        accessControlService);
    this.chunkRepository = chunkRepository;
  }

  @Override
  public boolean delete(final Knowledge knowledge) {
    return deleteChunksOfDeleted(knowledge.getId(), super.delete(knowledge));
  }

  @Override
  public boolean deleteByIdIgnoringVersion(final String id) {
    return deleteChunksOfDeleted(id, super.deleteByIdIgnoringVersion(id));
  }

  /** Stores new chunks of a knowledge the caller is indexing. */
  public void insertChunks(final List<KnowledgeChunk> chunks) {
    chunkRepository.insertMany(chunks);
  }

  /**
   * The chunks {@code query} matches within the knowledge {@code knowledgeIds} names, or within all
   * the knowledge the caller may read when it names none; knowledge the caller may not read is left
   * out.
   */
  public PaginatedResult<KnowledgeChunk> findChunks(
      final Collection<String> knowledgeIds, final Query query) {
    final Query source = query == null ? new Query() : query;
    final List<String> readableIds =
        CollectionUtils.isEmpty(knowledgeIds)
            ? findPermittedIds(new Query().withPage(Page.UNBOUNDED), Permission.READ).getItems()
            : List.copyOf(findPermittedIds(knowledgeIds, Permission.READ));
    final Filter readable = Filters.in(KnowledgeChunk.FIELD_KNOWLEDGE_ID, readableIds);
    final Filter completed = Filters.eq(KnowledgeChunk.FIELD_STATUS, IndexingStatus.COMPLETED.name());
    final Filter combined = source.getFilter() == null ? readable : Filters.and(source.getFilter(), readable);
    return chunkRepository.findByQuery(
        new Query(source)
            .withFilter(Filters.and(combined, completed)));
  }

  public void deleteChunksOfVersion(final String knowledgeId, final Long knowledgeVersion) {
    chunkRepository.deleteByFilterIgnoringVersion(
        Filters.and(
            Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_ID, knowledgeId),
            Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_VERSION, knowledgeVersion)));
  }

  public void deleteOlderVersions(final String knowledgeId, final Long knowledgeVersion) {
    requirePermission(knowledgeId, Permission.EDIT);
    chunkRepository.deleteByFilterIgnoringVersion(
        Filters.and(
            Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_ID, knowledgeId),
            Filters.lt(KnowledgeChunk.FIELD_KNOWLEDGE_VERSION, knowledgeVersion)));
  }

  public void updateChunkStatus(final String knowledgeId, final Long knowledgeVersion, final String status) {
    requirePermission(knowledgeId, Permission.EDIT);
    chunkRepository.updateManyIgnoringVersion(
        Filters.and(
            Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_ID, knowledgeId),
            Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_VERSION, knowledgeVersion)),
        Update.of(Operation.set(KnowledgeChunk.FIELD_STATUS, status)));
  }

  /**
   * Knowledge made within a session, such as an attached file or a fetched page, belongs to that
   * session for every user acting in it; knowledge a user adds directly is theirs.
   */
  @Override
  protected List<SharingChange> getInitialShare(final Knowledge knowledge) {
    return Context.currentPrincipal()
        // if user created then its scoped to user only, otherwise its scoped to any user acting in
        // the nested scoped like agent or session scoped across the users
        .map(creator -> creator.isUserActingDirectly() ? creator : creator.forAnyUser())
        .map(owner -> List.of(buildShare(knowledge, owner.toString(), StandardRole.MANAGER)))
        .orElse(List.of());
  }

  /** Deletes the chunks of the knowledge when it was deleted, and returns whether it was. */
  private boolean deleteChunksOfDeleted(final String knowledgeId, final boolean deleted) {
    if (deleted) {
      chunkRepository.deleteByFilterIgnoringVersion(
          Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_ID, knowledgeId));
    }
    return deleted;
  }
}
