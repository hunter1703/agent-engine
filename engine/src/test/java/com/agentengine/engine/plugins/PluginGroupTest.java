package com.agentengine.engine.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.InvocationContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginGroupTest {

  @Test
  void shouldForwardOnUserMessageCallbackAndShortCircuitOnFirstResult() {
    final BasePlugin first =
        new BasePlugin("first") {
          @Override
          public Maybe<Content> onUserMessageCallback(
              final InvocationContext invocationContext, final Content userMessage) {
            return Maybe.empty();
          }
        };
    final BasePlugin second =
        new BasePlugin("second") {
          @Override
          public Maybe<Content> onUserMessageCallback(
              final InvocationContext invocationContext, final Content userMessage) {
            return Maybe.just(
                Content.builder().role("user").parts(List.of(Part.fromText("rewritten"))).build());
          }
        };
    final BasePlugin third =
        new BasePlugin("third") {
          @Override
          public Maybe<Content> onUserMessageCallback(
              final InvocationContext invocationContext, final Content userMessage) {
            return Maybe.just(
                Content.builder().role("user").parts(List.of(Part.fromText("ignored"))).build());
          }
        };
    final PluginGroup pluginGroup = new PluginGroup("group", List.of(first, second, third));

    final Content result =
        pluginGroup
            .onUserMessageCallback(
                null, Content.builder().role("user").parts(List.of(Part.fromText("input"))).build())
            .blockingGet();

    assertThat(result.text()).isEqualTo("rewritten");
  }
}
