package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingFlowTest {

    private AbstractLLM routingModel;
    private BaseLlmFlow flowA;
    private BaseLlmFlow flowB;
    private FlowRoute routeA;
    private FlowRoute routeB;
    private InvocationContext context;

    @BeforeEach
    void setUp() {
        routingModel = mock(AbstractLLM.class);
        flowA = mock(BaseLlmFlow.class);
        flowB = mock(BaseLlmFlow.class);
        when(flowA.run(any())).thenReturn(Flowable.empty());
        when(flowB.run(any())).thenReturn(Flowable.empty());

        routeA = FlowRoute.of("ALPHA", "First route description", flowA);
        routeB = FlowRoute.of("BETA", "Second route description", flowB);

        final Session session = Session.builder("session")
                .appName("app")
                .userId("user")
                .state(new ConcurrentHashMap<>())
                .events(new ArrayList<>())
                .build();
        final LlmAgent agent = LlmAgent.builder().name("agent").build();
        context = InvocationContext.builder()
                .agent(agent)
                .session(session)
                .invocationId("inv-1")
                .sessionService(new InMemorySessionService())
                .artifactService(new InMemoryArtifactService())
                .build();
    }

    @Test
    void routesToFirstRouteWhenResponseMatchesFirstRouteName() {
        givenRoutingModelResponds("ALPHA");

        new RoutingFlow(routingModel, List.of(routeA, routeB)).run(context).blockingSubscribe();

        verify(flowA).run(context);
        verify(flowB, never()).run(any());
    }

    @Test
    void routesToSecondRouteWhenResponseMatchesSecondRouteName() {
        givenRoutingModelResponds("BETA");

        new RoutingFlow(routingModel, List.of(routeA, routeB)).run(context).blockingSubscribe();

        verify(flowB).run(context);
        verify(flowA, never()).run(any());
    }

    @Test
    void fallsBackToFirstRouteOnUnrecognizedResponse() {
        givenRoutingModelResponds("UNKNOWN_ROUTE_XYZ");

        new RoutingFlow(routingModel, List.of(routeA, routeB)).run(context).blockingSubscribe();

        verify(flowA).run(context);
        verify(flowB, never()).run(any());
    }

    @Test
    void fallsBackToFirstRouteOnNullResponse() {
        givenRoutingModelResponds(null);

        new RoutingFlow(routingModel, List.of(routeA, routeB)).run(context).blockingSubscribe();

        verify(flowA).run(context);
        verify(flowB, never()).run(any());
    }

    @Test
    void fallsBackToFirstRouteWhenRoutingModelThrows() {
        when(routingModel.generateContent(any(), anyBoolean()))
                .thenThrow(new RuntimeException("model unavailable"));

        new RoutingFlow(routingModel, List.of(routeA, routeB)).run(context).blockingSubscribe();

        verify(flowA).run(context);
        verify(flowB, never()).run(any());
    }

    @Test
    void doesNotWriteEventsToSessionDuringClassification() {
        givenRoutingModelResponds("ALPHA");
        final int eventsBefore = context.session().events().size();

        // Run just the classification by subscribing; flowA.run() returns empty so no events
        new RoutingFlow(routingModel, List.of(routeA, routeB)).run(context).blockingSubscribe();

        // Routing model call should not have added events
        verify(routingModel).generateContent(any(LlmRequest.class), anyBoolean());
        assertThat(context.session().events()).hasSize(eventsBefore);
    }

    @Test
    void rejectsEmptyRoutesList() {
        assertThatThrownBy(() -> new RoutingFlow(routingModel, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRoutesList() {
        assertThatThrownBy(() -> new RoutingFlow(routingModel, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenRoutingModelResponds(final String text) {
        final Content content = text == null
                ? Content.builder().role("model").parts(List.of(Part.fromText(""))).build()
                : Content.builder().role("model").parts(List.of(Part.fromText(text))).build();
        final LlmResponse response = LlmResponse.builder().content(content).build();
        when(routingModel.generateContent(any(), anyBoolean())).thenReturn(Flowable.just(response));
    }
}
