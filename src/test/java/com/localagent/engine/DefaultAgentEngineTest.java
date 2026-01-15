package com.localagent.engine;

import com.localagent.engine.context.BaseContextBuilder;
import com.localagent.engine.message.Message;
import com.localagent.engine.model.LLMModel;
import com.localagent.engine.state.InMemorySessionStore;
import com.localagent.engine.support.FakeLLMModel;
import com.localagent.engine.tools.AgentTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentEngineTest {
    @Test
    void executesToolAndReturnsFinal() {
        LLMModel reasoningModel = new FakeLLMModel(List.of(
            Message.assistant("TOOL_REQUEST:\n- tool: echo\n- inputs: {\"text\": \"hi\"}"),
            Message.assistant("FINAL: done")
        ));
        LLMModel toolModel = new FakeLLMModel(List.of(
            Message.assistant("TOOL_REQUEST:\n- tool: echo\n- inputs: {\"text\": \"hi\"}")
        ));
        AgentTool echoTool = new AgentTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "Echo text";
            }

            @Override
            public String execute(Map<String, Object> args) {
                return String.valueOf(args.get("text"));
            }
        };
        InMemorySessionStore store = new InMemorySessionStore();
        BaseContextBuilder contextManager = new BaseContextBuilder(Message.system("sys"), null, null, "think");
        DefaultAgentEngine engine = new DefaultAgentEngine(
            reasoningModel,
            toolModel,
            reasoningModel,
            null,
            List.of(echoTool),
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
            false
        );
        store.appendMessage("s1", Message.user("hello"));
        AgentResponse response = engine.invoke("s1");
        assertThat(response.finalText()).isEqualTo("finalAnswer");
        assertThat(store.getMessages("s1")).hasSize(5);
    }
}
