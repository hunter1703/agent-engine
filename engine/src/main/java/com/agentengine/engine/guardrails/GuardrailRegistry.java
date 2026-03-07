package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRule;
import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.plugins.PluginLoader;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class GuardrailRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(GuardrailRegistry.class);

  private final Map<GuardrailRuleType, GuardrailProvider<?>> typeVsProvider;

  @Inject
  public GuardrailRegistry(final @Any Instance<GuardrailProvider<?>> providerInstances) {
    this(collectProviders(providerInstances, PluginLoader.getClassLoader()));
  }

  public GuardrailRegistry(final List<GuardrailProvider<?>> providers) {
    this.typeVsProvider = CollectionUtils.transformToMap(providers, GuardrailProvider::type, provider -> provider);
  }

  public List<Guardrail> getGuardrailsForStage(
      final GuardrailsConfig config, final GuardrailStage stage) {
    if (stage == null) {
      return List.of();
    }
    final Map<GuardrailStage, List<Guardrail>> stageToGuardrails = getGuardrails(config);
    return CollectionUtils.nullSafeList(stageToGuardrails.get(stage));
  }

  private Map<GuardrailStage, List<Guardrail>> getGuardrails(final GuardrailsConfig config) {
    if (config == null || !config.isEnabled()) {
      return Map.of();
    }
    final Map<GuardrailStage, List<Guardrail>> stageVsGuardRails = new HashMap<>();

    for (final GuardrailRule rule : config.getRules()) {
      if (!rule.isEnabled()) {
        continue;
      }
      final GuardrailRuleType type = GuardrailRuleType.valueOfOrDefault(rule.getType());
      final GuardrailProvider<?> provider = typeVsProvider.get(type);
      if (provider == null) {
        LOG.warn("No guardrail provider registered for rule type '{}'.", type);
        continue;
      }
      final Guardrail created = createGuardrail(provider, config, rule);
      if (created != null) {
        stageVsGuardRails.computeIfAbsent(created.stage(), ignored -> new ArrayList<>()).add(created);
      }
    }
    return stageVsGuardRails;
  }

  private static List<GuardrailProvider<?>> collectProviders(final Instance<GuardrailProvider<?>> providers, final ClassLoader pluginLoader) {
    final List<GuardrailProvider<?>> allProviders = new ArrayList<>();
    for (final GuardrailProvider<?> provider : providers) {
      if (provider != null) {
        allProviders.add(provider);
      }
    }
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      //noinspection rawtypes
      final ServiceLoader<GuardrailProvider> loader = ServiceLoader.load(GuardrailProvider.class, pluginLoader);
      for (final GuardrailProvider<?> provider : loader) {
        if (provider != null) {
          allProviders.add(provider);
        }
      }
    }
    return allProviders;
  }

  @SuppressWarnings("unchecked")
  private static Guardrail createGuardrail(
      final GuardrailProvider<?> provider,
      final GuardrailsConfig config,
      final GuardrailRule rule) {
    return ((GuardrailProvider<GuardrailRule>) provider).create(config, rule);
  }
}
