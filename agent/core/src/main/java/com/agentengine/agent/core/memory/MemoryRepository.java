package com.agentengine.agent.core.memory;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.context.Context;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import com.agentengine.util.vectordb.VectorBackend;
import com.agentengine.util.vectordb.VectorRepositorySpec;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant-backed store of {@link Memory}: stores and semantically retrieves persistent memories,
 * each owned by the agent that made it, for the user it was made for. A memory is written with its
 * text already embedded.
 */
@Singleton
@Startup
public class MemoryRepository extends AbstractPermissionedRepository<Memory> {

  @Inject
  public MemoryRepository(
      final VectorBackend vectorBackend,
      final ModelProvider modelProvider,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        vectorBackend.getEntityStore(
            new VectorRepositorySpec<>(
                AssetClass.MEMORY,
                Memory.class,
                AgentVectorStoreClientType.MEMORY,
                MemoryRepository::toPayload,
                MemoryRepository::fromPayload,
                (modelId, query) -> {
                  try (RefCounted<Model.EmbeddingModel> refCounted =
                      modelProvider.getEmbeddingModel(modelId)) {
                    return refCounted.value().model().embed(query).content().vector();
                  }
                })),
        validationService,
        permissionChecker,
        accessControlService);
  }

  /**
   * Memories are created only by the runtime, for the agent and user they belong to, so no
   * permission on every asset is taken.
   */
  @Override
  protected void requireCreatePermission(final List<Memory> memories) {}

  /**
   * A memory belongs to the agent the creating principal acts in, for its user — not to the session
   * it was made in — so the agent recalls it in every later session with that user.
   */
  @Override
  protected List<SharingChange> getInitialShare(final Memory memory) {
    return Context.currentPrincipal()
        .map(
            creator ->
                List.of(
                    buildShare(
                        memory,
                        creator.actingIn(AssetClass.AGENT).toString(),
                        StandardRole.OWNER)))
        .orElse(List.of());
  }

  private static Map<String, Object> toPayload(final Memory memory) {
    final Map<String, Object> payload = new HashMap<>();
    payload.put(Memory.FIELD_AGENT_ID, memory.getAgentId());
    payload.put(Memory.FIELD_TEXT, memory.getText());
    return payload;
  }

  private static Memory fromPayload(final Map<String, Object> payload) {
    final Memory memory = new Memory();
    memory.setAgentId(CollectionUtils.getStringValueFromMap(payload, Memory.FIELD_AGENT_ID));
    memory.setText(CollectionUtils.getStringValueFromMap(payload, Memory.FIELD_TEXT));
    return memory;
  }
}
