package com.agentengine.agent.infra.factories.agent;

import com.agentengine.agent.infra.agents.ImageClassificationAgent;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ImageClassificationAgentConfig;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.agents.BaseAgent;
import io.quarkus.arc.WithCaching;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ImageClassificationAgentFactory
    implements AgentFactory<ImageClassificationAgentConfig, ImageClassificationAgent> {

  private final AgentService agentService;
  private final Instance<AgentProvider> agentProviderInstance;
  private final KnowledgeService knowledgeService;
  private final ModelProvider modelProvider;
  private final FileService fileService;

  @Inject
  public ImageClassificationAgentFactory(
      final AgentService agentService,
      @WithCaching final Instance<AgentProvider> agentProviderInstance,
      final KnowledgeService knowledgeService,
      final ModelProvider modelProvider,
      final FileService fileService) {
    this.agentService = agentService;
    this.agentProviderInstance = agentProviderInstance;
    this.knowledgeService = knowledgeService;
    this.modelProvider = modelProvider;
    this.fileService = fileService;
  }

  @Override
  public ImageClassificationAgent build(final ImageClassificationAgentConfig config) {
    final List<BaseAgent> classifiers = new ArrayList<>();
    for (final String subAgentId : CollectionUtils.nullSafeList(config.getSubAgentIds())) {
      final BaseAgentConfig subAgentConfig = agentService.getAgent(subAgentId);
      if (subAgentConfig == null) {
        throw new IllegalArgumentException("Sub agent config missing for id: " + subAgentId);
      }
      classifiers.add(agentProviderInstance.get().create(subAgentConfig));
    }
    return new ImageClassificationAgent(
        config, classifiers, knowledgeService, modelProvider, fileService);
  }

  @Override
  public String type() {
    return BaseAgentConfig.AgentType.IMAGE_CLASSIFICATION.type();
  }
}
