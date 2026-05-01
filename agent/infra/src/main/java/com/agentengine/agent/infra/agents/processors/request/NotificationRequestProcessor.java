package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.utils.Notification;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import dev.langchain4j.store.embedding.filter.logical.Not;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Injects the agent's notification map into the LLM request as a working-memory brief.
 *
 * <p>This processor is entirely generic — it knows nothing about plans, agents, or knowledge.
 * It simply reads whatever notifications are currently registered in {@link RunState}, groups
 * them by group, and renders each group as a titled section inside a structured brief.
 *
 * <p>Key formatting: snake_case keys (e.g. {@code spawned_agents}) are converted to
 * human-readable section titles (e.g. {@code SPAWNED AGENTS}).
 *
 * <p>Notifications persist across turns until explicitly removed via
 * {@link RunState#removeNotification(String)}. The processor never clears them — callers
 * are responsible for removing notifications when the condition they describe is resolved.
 */
public final class NotificationRequestProcessor implements RequestProcessor {
    public static final NotificationRequestProcessor INSTANCE = new NotificationRequestProcessor();

    private NotificationRequestProcessor() {}

    @Override
    public Single<RequestProcessingResult> processRequest(
            final InvocationContext context, final LlmRequest request) {
        final RunState runState = RunUtils.getOrInitState(context);
        final List<Notification> notifications = runState.notifications();

        if (notifications.isEmpty()) {
            return Single.just(RequestProcessingResult.create(request, List.of()));
        }

        final String brief = buildBrief(notifications);
        if (StringUtils.isBlank(brief)) {
            return Single.just(RequestProcessingResult.create(request, List.of()));
        }

        final LlmRequest updated =
                request.toBuilder().appendInstructions(List.of(brief)).build();
        return Single.just(RequestProcessingResult.create(updated, List.of()));
    }

    private static String buildBrief(final List<Notification> notifications) {
        final StringBuilder sb = new StringBuilder();
        sb.append(
                """
                ╔══════════════════════════════════════════════════════════════╗
                  REMINDERS — orient yourself before acting
                  Read this, reason through it, then decide your next step.
                ╚══════════════════════════════════════════════════════════════╝
                """);

        boolean hasContent = false;
        final Map<String, List<Notification>> notificationGroups = CollectionUtils.transformToMultiValuedMap(notifications, Notification::group, Function.identity());
        for (final Entry<String, List<Notification>> entry : notificationGroups.entrySet()) {
            final String title = entry.getKey().replace('_', ' ').toUpperCase();
            sb.append("\n▸ ").append(title).append(":\n");
            for (final Notification notification : entry.getValue()) {
                if (StringUtils.isNotBlank(notification.message())) {
                    sb.append("  • ").append(notification.message()).append("\n");
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
