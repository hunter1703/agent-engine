package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.utils.Reminder;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Injects the agent's reminder map into the LLM request as a working-memory brief.
 *
 * <p>This processor is entirely generic — it knows nothing about plans, agents, or knowledge.
 * It simply reads whatever reminders are currently registered in {@link RunState}, groups
 * them by group, and renders each group as a titled section inside a structured brief.
 *
 * <p>Key formatting: snake_case keys (e.g. {@code spawned_agents}) are converted to
 * human-readable section titles (e.g. {@code SPAWNED AGENTS}).
 *
 * <p>Reminders persist across turns until explicitly removed via
 * {@link RunState#removeReminder(String)}. The processor never clears them — callers
 * are responsible for removing reminders when the condition they describe is resolved.
 */
public final class ReminderRequestProcessor implements RequestProcessor {
    public static final ReminderRequestProcessor INSTANCE = new ReminderRequestProcessor();

    private ReminderRequestProcessor() {}

    @Override
    public Single<RequestProcessingResult> processRequest(
            final InvocationContext context, final LlmRequest request) {
        final RunState runState = RunUtils.getOrInitState(context);
        final List<Reminder> reminders = runState.reminders();

        if (reminders.isEmpty()) {
            return Single.just(RequestProcessingResult.create(request, List.of()));
        }

        final String brief = buildBrief(reminders);
        if (StringUtils.isBlank(brief)) {
            return Single.just(RequestProcessingResult.create(request, List.of()));
        }

        final LlmRequest updated =
                request.toBuilder().appendInstructions(List.of(brief)).build();
        return Single.just(RequestProcessingResult.create(updated, List.of()));
    }

    private static String buildBrief(final List<Reminder> reminders) {
        final StringBuilder sb = new StringBuilder();
        sb.append(
                """
                ╔══════════════════════════════════════════════════════════════╗
                  REMINDERS — orient yourself before acting
                  Read this, reason through it, then decide your next step.
                ╚══════════════════════════════════════════════════════════════╝
                """);

        boolean hasContent = false;
        final Map<String, List<Reminder>> reminderGroups = CollectionUtils.transformToMultiValuedMap(reminders, Reminder::group, Function.identity());
        for (final Entry<String, List<Reminder>> entry : reminderGroups.entrySet()) {
            final String title = entry.getKey().replace('_', ' ').toUpperCase();
            sb.append("\n▸ ").append(title).append(":\n");
            for (final Reminder reminder : entry.getValue()) {
                if (StringUtils.isNotBlank(reminder.message())) {
                    sb.append("  • ").append(reminder.message()).append("\n");
                }
            }
            hasContent = true;
        }

        if (!hasContent) {
            return null;
        }

        sb.append("""

                ──────────────────────────────────────────────────────────────
                Before acting: account for all items above in your next step.
                ──────────────────────────────────────────────────────────────
                """);

        return sb.toString().trim();
    }
}
