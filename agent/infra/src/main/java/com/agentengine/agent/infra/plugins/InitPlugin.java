package com.agentengine.agent.infra.plugins;

import static com.agentengine.agent.infra.utils.EventUtils.SENT_AT_KEY;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.reminders.ReminderSyncService;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import java.time.ZonedDateTime;

/**
 * Initializes session execution before agent execution:
 *
 * <ol>
 *   <li><b>Session State Bootstrap</b>: Initializes {@link SessionState} on agent entry, and makes
 *       sure the session has its scratchpad notebook.
 *   <li><b>Reminder Sync</b>: Brings the reminders about external resources, such as notebooks and
 *       non-indexed knowledge, up to date.
 *   <li><b>User Time Recording</b>: Records on {@link SessionState} the time, in the user's time
 *       zone, at which the latest message was sent.
 * </ol>
 */
public final class InitPlugin extends BasePlugin {

  private final NotebookService notebookService;
  private final PlanningValidator planningValidator;
  private final ReminderSyncService reminderSyncService;

  public InitPlugin(
      final NotebookService notebookService,
      final PlanningValidator planningValidator,
      final ReminderSyncService reminderSyncService) {
    super("init_plugin");
    this.notebookService = notebookService;
    this.planningValidator = planningValidator;
    this.reminderSyncService = reminderSyncService;
  }

  @Override
  public Maybe<Content> beforeAgentCallback(
      final BaseAgent agent, final CallbackContext callbackContext) {
    final SessionState sessionState =
        SessionUtils.getOrInitSessionState(callbackContext.invocationContext(), planningValidator);

    notebookService.ensureScratchpad(callbackContext.invocationContext().session().id());

    final String sentAtString =
        CollectionUtils.getStringValueFromMap(
            callbackContext.invocationContext().session().state(), SENT_AT_KEY);
    if (StringUtils.isNotBlank(sentAtString)) {
      sessionState.updateUserTime(ZonedDateTime.parse(sentAtString));
    }
    reminderSyncService.syncAll(sessionState);
    return Maybe.empty();
  }
}
