package com.agentengine.engine.agents;

import static com.agentengine.engine.infra.TitleConfig.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.infra.TitleConfig;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionTitleGeneratorTest {

  private InfraMongoRepository infraMongoRepository;
  private ModelProvider modelProvider;
  private BaseLlm baseLlm;
  private SessionTitleGenerator generator;

  @BeforeEach
  void setUp() {
    infraMongoRepository = mock(InfraMongoRepository.class);
    modelProvider = mock(ModelProvider.class);
    baseLlm = mock(BaseLlm.class);
  }

  @Test
  void generateTitle_success() {
    TitleConfig config = new TitleConfig();
    config.setModelId("test-model");
    when(infraMongoRepository.findOneByType(TYPE)).thenReturn(config);
    when(modelProvider.get(any(AgentModelConfig.class))).thenReturn(baseLlm);

    LlmResponse response = mock(LlmResponse.class);
    Content content = Content.builder().parts(Part.builder().text("Test Title").build()).build();
    when(response.content()).thenReturn(Optional.of(content));
    when(baseLlm.generateContent(any(), any(Boolean.class))).thenReturn(Flowable.just(response));

    generator = new SessionTitleGenerator(infraMongoRepository, modelProvider);

    Event event = mock(Event.class);
    Content eventContent = Content.builder().parts(Part.builder().text("Hello").build()).build();
    when(event.content()).thenReturn(Optional.of(eventContent));

    Optional<String> title = generator.generateTitle(List.of(event));

    assertThat(title).isPresent().contains("Test Title");
  }

  @Test
  void generateTitle_missingConfig() {
    when(infraMongoRepository.findOneByType(TYPE)).thenReturn(null);
    generator = new SessionTitleGenerator(infraMongoRepository, modelProvider);

    Optional<String> title = generator.generateTitle(List.of(mock(Event.class)));

    assertThat(title).isEmpty();
  }

  @Test
  void generateTitle_emptyEvents() {
    TitleConfig config = new TitleConfig();
    config.setModelId("test-model");
    when(infraMongoRepository.findOneByType(TYPE)).thenReturn(config);
    when(modelProvider.get(any())).thenReturn(baseLlm);
    generator = new SessionTitleGenerator(infraMongoRepository, modelProvider);

    Optional<String> title = generator.generateTitle(List.of());

    assertThat(title).isEmpty();
  }

  @Test
  void generateTitle_sanitizesTitle() {
    TitleConfig config = new TitleConfig();
    config.setModelId("test-model");
    when(infraMongoRepository.findOneByType(TYPE)).thenReturn(config);
    when(modelProvider.get(any())).thenReturn(baseLlm);

    LlmResponse response = mock(LlmResponse.class);
    Content content =
        Content.builder().parts(Part.builder().text("  \"Sanitized Title\"  ").build()).build();
    when(response.content()).thenReturn(Optional.of(content));
    when(baseLlm.generateContent(any(), any(Boolean.class))).thenReturn(Flowable.just(response));

    generator = new SessionTitleGenerator(infraMongoRepository, modelProvider);

    Event event = mock(Event.class);
    Content eventContent = Content.builder().parts(Part.builder().text("Hello").build()).build();
    when(event.content()).thenReturn(Optional.of(eventContent));

    Optional<String> title = generator.generateTitle(List.of(event));

    assertThat(title).isPresent().contains("Sanitized Title");
  }
}
