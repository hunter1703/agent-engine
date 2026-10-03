package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.agent.core.session.SessionActorFactory;
import com.agentengine.agent.core.session.commands.SelfCommand.AwaitChildCommand;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.agent.infra.utils.ToolUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Principal;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractAgentTool extends Tool {

  public static final String CHILD_SESSION_ID = "child_session_id";

  public static final String GOAL_SCHEMA_DESCRIPTION =
      """
      A concise, one-sentence summary of what this exchange with the child is for — specific enough that you (or a later, unrelated turn) can tell its purpose at a glance without rereading the message. Do not restate or paraphrase the message itself here — that belongs in 'message'. Do not leave it vague either (e.g. 'chat', 'follow-up', 'task') — name the concrete objective or question.""";

  public static final String NOTEBOOKS_ACCESS_DESCRIPTION =
      "Grants the ability to read every note in it, and to add, change and delete notes.";

  public static final String KNOWLEDGES_ACCESS_DESCRIPTION =
      "Grants the ability to search it (if indexed) or read it in full (if not) with "
          + Constants.ToolNames.SEARCH_KNOWLEDGE
          + ".";

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAgentTool.class);
  protected final ActorSystemProvider actorSystemProvider;
  protected final NotebookService notebookService;
  protected final KnowledgeService knowledgeService;
  protected final AccessControlService accessControlService;

  protected AbstractAgentTool(
      final ToolDescriptor toolDescriptor, final ActorSystemProvider actorSystemProvider) {
    this(toolDescriptor, actorSystemProvider, null, null, null);
  }

  protected AbstractAgentTool(
      final ToolDescriptor toolDescriptor,
      final ActorSystemProvider actorSystemProvider,
      final NotebookService notebookService,
      final KnowledgeService knowledgeService,
      final AccessControlService accessControlService) {
    super(toolDescriptor, true);
    this.actorSystemProvider = actorSystemProvider;
    this.notebookService = notebookService;
    this.knowledgeService = knowledgeService;
    this.accessControlService = accessControlService;
  }

  protected EntityRef<SessionCommand> actorRef(final ToolContext toolContext) {
    final String sessionId = ToolUtils.sessionId(toolContext);
    return actorSystemProvider.entityRefFor(
        SessionActor.TYPE_KEY, SessionActorFactory.entityId(sessionId));
  }

  protected static String buildFullMessage(final String goal, final String message) {
    return StringUtils.isNotBlank(goal) ? "Goal: " + goal + "\n\nMessage: " + message : message;
  }

  protected ToolOutput<Map<String, Object>> validateGrants(
      final List<String> notebookIds, final List<String> knowledgeIds) {
    final List<Violation> violations = validate(notebookIds, knowledgeIds);
    if (violations.isEmpty()) {
      return null;
    }
    return ToolOutput.direct(
        Map.of("error", String.join(" ", violations.stream().map(Violation::message).toList())));
  }

  /**
   * Shares the granted notebooks and knowledge with the grantee session in one change, or none of
   * them when the caller may not share one; returns the error to hand back in that case. Each is
   * shared with the session for every user acting in it.
   */
  protected ToolOutput<Map<String, Object>> issueGrants(
      final String granteeAgentId,
      final String granteeSessionId,
      final List<String> notebookIds,
      final List<String> knowledgeIds) {
    final Principal granteeSession = AgentSession.principal(granteeAgentId, granteeSessionId);
    final List<SharingChange> changes =
        new ArrayList<>(notebookService.sharingChanges(granteeSession, notebookIds));
    for (final String knowledgeId : CollectionUtils.nullSafeList(knowledgeIds)) {
      changes.add(shareKnowledge(knowledgeId, granteeSession.toString()));
    }
    if (changes.isEmpty()) {
      return null;
    }
    try {
      accessControlService.updateSharing(changes);
      return null;
    } catch (final UnauthorizedException exception) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "You can't grant this access: you lack the permission to share %s '%s'."
                  .formatted(exception.getAssetType(), exception.getAssetId())));
    }
  }

  protected ToolOutput<Map<String, Object>> awaitChild(
      final ToolContext toolContext, final String childSessionId) {

    final ToolOutput<Map<String, Object>> completedResult = getResultIfCompleted(toolContext);
    if (completedResult != null) {
      return completedResult;
    }

    final RunResult result =
        actorRef(toolContext)
            .<RunResult>ask(
                replyTo -> new AwaitChildCommand(childSessionId, replyTo), Duration.ofMinutes(30))
            .toCompletableFuture()
            .join();

    if (!result.completedRun()) {
      toolContext.requestConfirmation(
          "Waiting for child agent run to complete.", Map.of(CHILD_SESSION_ID, childSessionId));
      LOGGER.debug("Awaiting child session {} to complete.", childSessionId);
      return ToolOutput.empty();
    } else {
      LOGGER.debug("Child session {} completed with result: {}", childSessionId, result);
    }
    SessionUtils.getSessionState(toolContext.invocationContext())
        .markSpawnedAgentAwaited(childSessionId);
    return ToolOutput.direct(AwaitAgentTool.buildCompletedResponseMap(childSessionId, result));
  }

  protected ToolOutput<Map<String, Object>> getResultIfCompleted(final ToolContext toolContext) {
    final Optional<ToolConfirmation> toolConfirmationOptional = toolContext.toolConfirmation();
    if (toolConfirmationOptional.isPresent()) {
      final ToolConfirmation toolConfirmation = toolConfirmationOptional.get();
      if (toolConfirmation.confirmed()) {
        //noinspection unchecked
        return ToolOutput.direct((Map<String, Object>) toolConfirmation.payload());
      }
    }
    return null;
  }

  private List<Violation> validate(
      final List<String> notebookIds, final List<String> knowledgeIds) {
    final List<Violation> violations =
        new ArrayList<>(notebookService.grantViolations(notebookIds));

    final Set<String> readableKnowledgeIds =
        CollectionUtils.isEmpty(knowledgeIds)
            ? Set.of()
            : knowledgeService.findPermittedIds(knowledgeIds, Permission.READ);
    for (final String knowledgeId : CollectionUtils.nullSafeList(knowledgeIds)) {
      if (!readableKnowledgeIds.contains(knowledgeId)) {
        violations.add(
            Violation.builder("knowledge_not_found")
                .message(
                    "Grant for knowledge '"
                        + knowledgeId
                        + "' targets a knowledge item that doesn't exist.")
                .detail("knowledgeId", knowledgeId)
                .build());
      }
    }

    return violations;
  }

  private static SharingChange shareKnowledge(final String knowledgeId, final String principal) {
    return SharingChange.ofAsset(
        AssetClass.KNOWLEDGE, knowledgeId, Map.of(principal, Set.of(StandardRole.READER)));
  }
}
