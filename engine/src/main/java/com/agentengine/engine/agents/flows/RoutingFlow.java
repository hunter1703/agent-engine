package com.agentengine.engine.agents.flows;

import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies the current invocation context against a set of {@link FlowRoute}s using a
 * lightweight LLM call, then delegates to the matching flow.
 *
 * <p>Classification is read-only: no events are written to the session during the routing call.
 * Any failure in classification falls back silently to the first route.
 */
public final class RoutingFlow extends AbstractFlow {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingFlow.class);
    private static final int DEFAULT_HISTORY_SIZE = 10;

    private final AbstractLLM routingModel;
    private final List<FlowRoute> routes;
    private final int historySize;

    /**
     * @param historySize number of recent session events to pass to the routing model.
     *                    0 = only the last user message; positive = last N events.
     */
    public RoutingFlow(final AbstractLLM routingModel, final List<FlowRoute> routes, final int historySize) {
        super(Integer.MAX_VALUE, List.of(), List.of());
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("RoutingFlow requires at least one route");
        }
        this.routingModel = routingModel;
        this.routes = List.copyOf(routes);
        this.historySize = historySize;
    }

    public RoutingFlow(final AbstractLLM routingModel, final List<FlowRoute> routes) {
        this(routingModel, routes, DEFAULT_HISTORY_SIZE);
    }

    @Override
    public Flowable<Event> run(final InvocationContext context) {
        final FlowRoute route = classify(context);
        return route.flow().run(context);
    }

    private FlowRoute classify(final InvocationContext context) {
        try {
            final List<Content> historyContents = getHistoryContents(context);
            final List<Content> contents = historyContents.isEmpty()
                    ? List.of(Content.builder()
                            .role("user")
                            .parts(Part.builder().text("(No conversation history yet.)").build())
                            .build())
                    : historyContents;
            final LlmRequest request = LlmRequest.builder()
                    .appendInstructions(List.of(buildRoutingPrompt()))
                    .contents(contents)
                    .build();
            final LlmResponse response = routingModel.generateContent(request, false).blockingFirst();
            final String rawText = response.content().map(Content::text).orElse("").trim();
            return matchRoute(rawText);
        } catch (final Exception e) {
            LOG.warn("Routing classification failed, falling back to first route", e);
            return routes.getFirst();
        }
    }

    private List<Content> getHistoryContents(final InvocationContext context) {
        final List<Event> events = context.session().events();
        if (historySize == 0) {
            return events.reversed().stream()
                    .map(e -> e.content().orElse(null))
                    .filter(c -> c != null && "user".equals(c.role().orElse(null)))
                    .limit(1)
                    .toList();
        }
        final int startIndex = Math.max(0, events.size() - historySize);
        return events.subList(startIndex, events.size()).stream()
                .map(e -> e.content().orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildRoutingPrompt() {
        final StringBuilder sb = new StringBuilder();
        sb.append("You are a routing agent. Select which flow best handles the user's most recent message.\n\n");
        sb.append("Available flows:\n");
        for (final FlowRoute route : routes) {
            sb.append("- ").append(route.name()).append(": ").append(route.description()).append("\n");
        }
        sb.append("\nReply with ONLY the flow name (e.g. ").append(routes.getFirst().name()).append("). No explanation.");
        return sb.toString();
    }

    private FlowRoute matchRoute(final String text) {
        final String normalized = text.trim().toUpperCase();
        for (final FlowRoute route : routes) {
            if (normalized.contains(route.name().toUpperCase())) {
                return route;
            }
        }
        LOG.warn("Could not match route from response '{}', falling back to first route", text);
        return routes.getFirst();
    }
}
