package com.localagent.engine;

import com.localagent.engine.context.BaseContextBuilder;
import com.localagent.engine.message.Message;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.state.InMemorySessionStore;
import com.localagent.engine.support.FakeLLMModel;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentEngineRepairTest {
    @Test
    void repairsInvalidReasonerResponse() {
        LLMModel reasoningModel = new FakeLLMModel(List.of(
            Message.assistant("oops"),
            Message.assistant("FINAL: fixed")
        ));
        InMemorySessionStore store = new InMemorySessionStore();
        BaseContextBuilder contextManager = new BaseContextBuilder(
            Message.system("sys"),
            Message.system("protocol"),
            null,
            "think"
        );
        DefaultAgentEngine engine = new DefaultAgentEngine(
            reasoningModel,
            reasoningModel,
            reasoningModel,
            null,
            List.of(),
            contextManager,
            store,
            5,
            1,
            true,
            "think",
            true,
            null,
            null,
            false,
            true
        );
        store.appendMessage("s1", Message.user("hi"));
        AgentResponse response = engine.invoke("s1");
        assertThat(response.finalText()).isEqualTo("fixed");
    }
}
