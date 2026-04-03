package com.agentengine.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.runtime.session.events.TurnCommittedFact;
import com.agentengine.runtime.session.state.SessionActorState;
import com.agentengine.runtime.session.state.SessionState;
import com.google.adk.events.Event;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionActorTurnCommittedTest {

    @Test
    void shouldKeepSessionPausedWhenPausedTurnIsCommittedWithoutFinalAnswer() {
        final SessionActorState pausedState = SessionActorState.initial().selfPaused("confirmation-1");
        final Event event = Event.builder().id("event-1").build();

        final SessionActorState newState =
                SessionActor.applyCommittedTurn(pausedState, new TurnCommittedFact(List.of(event), null, null));

        assertThat(newState.sessionState()).isEqualTo(SessionState.PAUSED);
        assertThat(newState.lastCommittedEvents()).isEqualTo("event-1");
    }

    @Test
    void shouldReturnRunningWhenActiveTurnIsCommittedWithoutFinalAnswer() {
        final SessionActorState runningState = SessionActorState.initial().withSessionState(SessionState.RUNNING);
        final Event event = Event.builder().id("event-2").build();

        final SessionActorState newState =
                SessionActor.applyCommittedTurn(runningState, new TurnCommittedFact(List.of(event), null, null));

        assertThat(newState.sessionState()).isEqualTo(SessionState.RUNNING);
        assertThat(newState.lastCommittedEvents()).isEqualTo("event-2");
    }
}
