package com.agentengine.agent.infra.plugins;

import static com.agentengine.agent.infra.utils.EventUtils.SENT_AT_KEY;

import com.agentengine.agent.infra.reminders.ReminderSyncService;
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
 *   <li><b>Session State Bootstrap</b>: Initializes {@link SessionState} on agent entry, syncing
 *       external resources (such as notebooks and non-indexed knowledge) when a {@link
 *       ReminderSyncService} is provided.
 *   <li><b>User Context Recording</b>: Captures {@code sent_at} from invocation session state into
 *       {@link SessionUserContext} on {@link SessionState}, recording the message timestamp and
 *       detecting any time zone transitions across user messages.
 * </ol>
 */
public final class InitPlugin extends BasePlugin {
  private final ReminderSyncService reminderSyncService;

  public InitPlugin() {
    this(null);
  }

  public InitPlugin(final ReminderSyncService reminderSyncService) {
    super("init_plugin");
    this.reminderSyncService = reminderSyncService;
  }

  @Override
  public Maybe<Content> beforeAgentCallback(
      final BaseAgent agent, final CallbackContext callbackContext) {
    final SessionState sessionState =
        SessionUtils.getOrInitSessionState(callbackContext.invocationContext());

    final String sentAtString =
        CollectionUtils.getStringValueFromMap(
            callbackContext.invocationContext().session().state(), SENT_AT_KEY);
    if (StringUtils.isNotBlank(sentAtString)) {
      sessionState.updateUserTime(ZonedDateTime.parse(sentAtString));
    }

    if (reminderSyncService != null) {
      reminderSyncService.syncAll(sessionState);
    }
    return Maybe.empty();
  }
}
