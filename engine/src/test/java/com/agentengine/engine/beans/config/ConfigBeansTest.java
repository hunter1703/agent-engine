package com.agentengine.engine.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigBeansTest {

  @Test
  void mongoStateStoreConfigStoresValues() {
    MongoStateStoreConfig config = new MongoStateStoreConfig();
    config.setUri("mongodb://localhost");
    config.setDatabase("db");
    config.setSessionsCollection("sessions");
    config.setMessagesCollection("messages");
    config.setToolExecsCollection("tool_execs");
    config.setSummariesCollection("summaries");

    assertThat(config.getType()).isEqualTo("mongo");
    assertThat(config.getUri()).isEqualTo("mongodb://localhost");
    assertThat(config.getDatabase()).isEqualTo("db");
    assertThat(config.getSessionsCollection()).isEqualTo("sessions");
    assertThat(config.getMessagesCollection()).isEqualTo("messages");
    assertThat(config.getToolExecsCollection()).isEqualTo("tool_execs");
    assertThat(config.getSummariesCollection()).isEqualTo("summaries");

    config.setType("custom");
    assertThat(config.getType()).isEqualTo("custom");
  }

  @Test
  void summarizingContextConfigStoresValues() {
    SummarizingContextConfig config = new SummarizingContextConfig();
    config.setTriggerThreshold(0.7);
    config.setRecencyThreshold(0.2);
    config.setSummarizerModel("summary-model");

    assertThat(config.getType()).isEqualTo("summarize");
    assertThat(config.getTriggerThreshold()).isEqualTo(0.7);
    assertThat(config.getRecencyThreshold()).isEqualTo(0.2);
    assertThat(config.getSummarizerModel()).isEqualTo("summary-model");

    config.setType("custom");
    assertThat(config.getType()).isEqualTo("custom");
  }

  @Test
  void modelConfigStoresValues() {
    ModelConfig config = new ModelConfig();
    config.setBaseUrl("http://localhost");
    config.setProvider("OPEN_AI");
    config.setModel("gpt-4");
    config.setTemperature(0.1);
    config.setTopK(4);
    config.setTopP(0.9);
    config.setRepeatPenalty(1.1);
    config.setNumPredict(128);
    config.setMaxContextLength(8192);
    config.setStopTokens(List.of("stop"));
    config.setResponseFormat("text");
    config.setThoughtsStartTag("<t>");
    config.setThoughtsEndTag("</t>");
    config.setThoughtsEnabled(true);
    config.setContextConfig(new LastNContextConfig());

    assertThat(config.getBaseUrl()).isEqualTo("http://localhost");
    assertThat(config.getProvider()).isEqualTo("OPEN_AI");
    assertThat(config.getModel()).isEqualTo("gpt-4");
    assertThat(config.getTemperature()).isEqualTo(0.1);
    assertThat(config.getTopK()).isEqualTo(4);
    assertThat(config.getTopP()).isEqualTo(0.9);
    assertThat(config.getRepeatPenalty()).isEqualTo(1.1);
    assertThat(config.getNumPredict()).isEqualTo(128);
    assertThat(config.getMaxContextLength()).isEqualTo(8192);
    assertThat(config.getStopTokens()).containsExactly("stop");
    assertThat(config.getResponseFormat()).isEqualTo("text");
    assertThat(config.getThoughtsStartTag()).isEqualTo("<t>");
    assertThat(config.getThoughtsEndTag()).isEqualTo("</t>");
    assertThat(config.isThoughtsEnabled()).isTrue();
    assertThat(config.getContextConfig()).isInstanceOf(LastNContextConfig.class);
  }

  @Test
  void modelConfigDefaultsContextConfig() {
    ModelConfig config = new ModelConfig();

    assertThat(config.getContextConfig()).isInstanceOf(LastNContextConfig.class);
  }

  @Test
  void routerEngineConfigValidatesRequiredFields() {
    RouterEngineConfig config = new RouterEngineConfig();
    config.setReasoning("reasoner.json");
    config.setSystemPrompt("prompt");
    config.setRouter("router.json");
    config.setTool("tool.json");

    config.validate();

    RouterEngineConfig missingRouter = new RouterEngineConfig();
    missingRouter.setReasoning("reasoner.json");
    missingRouter.setSystemPrompt("prompt");

    assertThatThrownBy(missingRouter::validate).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("engine.router");

    RouterEngineConfig missingTool = new RouterEngineConfig();
    missingTool.setReasoning("reasoner.json");
    missingTool.setSystemPrompt("prompt");
    missingTool.setRouter("router.json");

    assertThatThrownBy(missingTool::validate).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("engine.router requires");
  }
}
