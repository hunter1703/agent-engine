package com.agentengine.engine.services;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.services.AgentExecutionService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class AgentResilienceTest {

        @Inject
        AgentExecutionService agentExecutionService;

        AgentSessionRuntimeManager agentSessionRuntimeManager;
        AgentRunner agentRunner;

        @BeforeEach
        void setUpMocks() {
                agentSessionRuntimeManager = Mockito.mock(AgentSessionRuntimeManager.class);
                QuarkusMock.installMockForType(agentSessionRuntimeManager, AgentSessionRuntimeManager.class);

                agentRunner = Mockito.mock(AgentRunner.class);
                QuarkusMock.installMockForType(agentRunner, AgentRunner.class);
        }

        @Test
        void testRetryOnRun() {
                // Configure mock to throw exception
                when(agentSessionRuntimeManager.getOrStartRuntime(anyString(), anyString()))
                                .thenThrow(new RuntimeException("Simulated failure"));

                AgentRequest request = new AgentRequest();
                request.setAgentId("test-agent");
                request.setSessionId("test-session");
                request.setMessage("hello");

                // Expect RuntimeException after retries
                assertThatThrownBy(() -> agentExecutionService.run(request))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessage("Simulated failure");

                // Verify that it was called more than once (e.g. 1 initial + 2 retries = 3
                // calls)
                // Default maxRetries=2 means 3 total attempts
                verify(agentSessionRuntimeManager, times(3)).getOrStartRuntime(anyString(), anyString());
        }
}
