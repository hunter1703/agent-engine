package com.agentengine.engine.agents.flows;

import com.agentengine.engine.utils.ViolationUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractFlow extends SingleFlow {
    private final Set<String> activeInvocations = ConcurrentHashMap.newKeySet();

    public AbstractFlow(final int maxSteps, final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
        super(requestProcessors, responseProcessors, Optional.of(maxSteps));
    }

    private static final int MAX_RECURSION_DEPTH = 10;

    @Override
    public Flowable<Event> run(final InvocationContext invocationContext) {
        return runRecursive(invocationContext, 0, false);
    }

    @Override
    public Flowable<Event> runLive(final InvocationContext invocationContext) {
        return runRecursive(invocationContext, 0, true);
    }

    private Flowable<Event> runRecursive(final InvocationContext context, final int depth, final boolean live) {
        if (depth >= MAX_RECURSION_DEPTH) {
            return Flowable.empty();
        }
        final Flowable<Event> flow = live ? super.runLive(context) : super.run(context);
        return runWithReset(context, flow).concatWith(Flowable.defer(() -> {
            if (shouldResume(context)) {
                if (depth + 1 >= MAX_RECURSION_DEPTH) {
                    ViolationUtils.addViolation(context, com.agentengine.engine.utils.Violation.builder("premature_termination_limit")
                            .message("Recursion limit reached")
                            .correctionMessage("System reached maximum recursion depth. Terminating flow.")
                            .build());
                    return Flowable.empty();
                }
                return runRecursive(context, depth + 1, live);
            }
            return Flowable.empty();
        }));
    }

    private boolean shouldResume(final InvocationContext context) {
        return ViolationUtils.getViolations(context).stream()
                .anyMatch(v -> v.getDetail("resume_needed", false));
    }

    private Flowable<Event> runWithReset(
            final InvocationContext invocationContext, final Flowable<Event> flowable) {
        final String invocationId = invocationContext.invocationId();
        final boolean firstInvocation = activeInvocations.add(invocationId);
        if (firstInvocation) {
            return flowable.doFinally(() -> activeInvocations.remove(invocationId));
        }
        return flowable;
    }
}
