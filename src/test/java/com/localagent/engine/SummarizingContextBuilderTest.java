package com.localagent.engine;

import com.localagent.engine.context.SummarizingContextBuilder;
import com.localagent.engine.message.Message;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.state.InMemorySessionStore;
import com.localagent.engine.support.FakeLLMModel;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummarizingContextBuilderTest {
    @Test
    void storesSummaryWhenThresholdExceeded() {
        LLMModel summarizer = new FakeLLMModel(List.of(
            Message.assistant("summary")
        ));
        InMemorySessionStore store = new InMemorySessionStore();
        SummarizingContextBuilder manager = new SummarizingContextBuilder(
            Message.system("sys"),
            Message.system("protocol"),
            null,
            "think",
            summarizer,
            1,
            0.1,
            0.1,
            10,
            store
        );
        String sessionId = "s1";
        store.appendMessage(sessionId, Message.user("hello"));
        store.appendMessage(sessionId, Message.assistant("FINAL: response"));
        manager.buildPrompt(new SessionState(sessionId, store.getMessages(sessionId)));
        assertThat(store.getSummaries(sessionId)).isNotEmpty();
    }
}
