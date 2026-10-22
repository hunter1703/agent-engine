package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.StartSessionResult;
import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.tenancy.AccessControlService;
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
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A tool that hands work to a child session: it gives the child access to notebooks and knowledge,
 * delivers a message, and then waits for the child or not.
 */
public abstract class DelegationTool extends AbstractAgentTool {

  public static final String GOAL_SCHEMA_DESCRIPTION =
      "One sentence naming what you delegate, shown in your reminders so you can keep track of it.";

  public static final String NOTEBOOKS_ACCESS_DESCRIPTION =
      "Grants the ability to read every note in it, and to add, change and delete notes.";

  public static final String KNOWLEDGES_ACCESS_DESCRIPTION =
      "Grants the ability to search it (if indexed) or read it in full (if not).";

  private final NotebookService notebookService;
  private final KnowledgeService knowledgeService;
  private final AccessControlService accessControlService;

  protected DelegationTool(
      final ToolDescriptor toolDescriptor,
      final ActorSystemProvider actorSystemProvider,
      final NotebookService notebookService,
      final KnowledgeService knowledgeService,
      final AccessControlService accessControlService,
      final ReminderSyncService reminderSyncService) {
    super(toolDescriptor, actorSystemProvider, reminderSyncService);
    this.notebookService = notebookService;
    this.knowledgeService = knowledgeService;
    this.accessControlService = accessControlService;
  }

  /** Whether the caller waits for the child's result: it does unless it said it does not. */
  protected static boolean waitsForChild(final Boolean awaitCompletion) {
    return awaitCompletion == null || awaitCompletion;
  }

  protected static String buildFullMessage(final String goal, final String message) {
    return StringUtils.isNotBlank(goal) ? "Goal: " + goal + "\n\nMessage: " + message : message;
  }

  /**
   * Shares the notebooks and knowledge with the child session, for every user acting in it: all of
   * them in one change, or none when the caller may not share one.
   *
   * @throws GrantRefused when one of them does not exist or the caller may not share it
   */
  protected void grantAccess(
      final String childAgentId,
      final String childSessionId,
      final List<String> notebookIds,
      final List<String> knowledgeIds) {
    requireExisting(notebookIds, knowledgeIds);
    final Principal childSession = AgentSession.principal(childAgentId, childSessionId);
    final List<SharingChange> changes =
        new ArrayList<>(notebookService.sharingChanges(childSession, notebookIds));
    for (final String knowledgeId : CollectionUtils.nullSafeList(knowledgeIds)) {
      changes.add(
          SharingChange.ofAsset(
              AssetClass.KNOWLEDGE,
              knowledgeId,
              Map.of(childSession.toString(), Set.of(StandardRole.READER))));
    }
    if (changes.isEmpty()) {
      return;
    }
    try {
      accessControlService.updateSharing(changes);
    } catch (final UnauthorizedException exception) {
      throw new GrantRefused(
          "You can't grant this access: you lack the permission to share %s '%s'."
              .formatted(exception.getAssetType(), exception.getAssetId()));
    }
  }

  /**
   * What the caller gets once {@code result} says whether the child took the message: the failure,
   * the child's own result when the caller waits, else that it started.
   *
   * @param await whether the caller waits for the child's result
   * @param onDelivered notes the child in the session's state, once the child took the message
   * @param started what a child that was not waited for answers
   * @param failure how the caller's failure to hand the message over starts
   */
  protected ToolOutput<Map<String, Object>> afterDelivery(
      final ToolContext toolContext,
      final String childSessionId,
      final StartSessionResult result,
      final boolean await,
      final Runnable onDelivered,
      final Map<String, Object> started,
      final String failure) {
    if (!(result instanceof StartSessionResult.Accepted)) {
      final String reason =
          result instanceof StartSessionResult.Rejected(String rejection)
              ? rejection
              : "unexpected response";
      return ToolOutput.direct(Map.of("error", failure + ": " + reason));
    }
    onDelivered.run();
    return await ? awaitChild(toolContext, childSessionId) : ToolOutput.direct(started);
  }

  private void requireExisting(final List<String> notebookIds, final List<String> knowledgeIds) {
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
                .build());
      }
    }
    if (!violations.isEmpty()) {
      throw new GrantRefused(
          String.join(" ", violations.stream().map(Violation::message).toList()));
    }
  }

  /** The access asked for cannot be granted; the message says why, for the model. */
  protected static final class GrantRefused extends RuntimeException {
    private GrantRefused(final String message) {
      super(message);
    }
  }
}
