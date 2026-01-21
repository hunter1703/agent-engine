package com.agentengine.interfaces.rest.handlers;

import com.agui.core.agent.AgentSubscriberParams;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunStartedEvent;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

public class SSESubscriberTest {

    @Test
    public void testSSESubscriberCanBeInstantiated() {
        @SuppressWarnings("unchecked")
        MultiEmitter<BaseEvent> mockEmitter = Mockito.mock(MultiEmitter.class);

        // This should compile and run without errors if our SSESubscriber is properly implemented
        SSESubscriber subscriber = new SSESubscriber(mockEmitter);

        // Verify that we can call methods without errors
        AgentSubscriberParams mockParams = Mockito.mock(AgentSubscriberParams.class);
        subscriber.onRunInitialized(mockParams);

        // Verify emitter was called
        Mockito.verify(mockEmitter).emit(any(RunStartedEvent.class));
    }
}