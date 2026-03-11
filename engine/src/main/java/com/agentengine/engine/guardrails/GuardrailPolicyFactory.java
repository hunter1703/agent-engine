package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.GuardrailExecutionMode;
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
public final class GuardrailPolicyFactory {
  private static final Logger LOG = LoggerFactory.getLogger(GuardrailPolicyFactory.class);

  private final Map<GuardrailRuleType, GuardrailProvider<?>> typeVsProvider;

  @Inject
  public GuardrailPolicyFactory(final @Any Instance<GuardrailProvider<?>> providerInstances) {
    this(collectProviders(providerInstances, PluginLoader.getClassLoader()));
  }

  public GuardrailPolicyFactory(final List<GuardrailProvider<?>> providers) {
    this.typeVsProvider =
        CollectionUtils.transformToMap(providers, GuardrailProvider::type, provider -> provider);
  }

  public GuardrailPolicy build(final GuardrailsConfig config) {
    if (config == null || !config.isEnabled()) {
      return GuardrailPolicy.disabled();
    }
    final Map<GuardrailStage, List<Guardrail>> stageVsGuardRails =
        new EnumMap<>(GuardrailStage.class);

    for (final GuardrailRule rule : CollectionUtils.nullSafeList(config.getRules())) {
      if (!rule.isEnabled()) {
        continue;
      }
      final GuardrailRuleType type = GuardrailRuleType.valueOfOrDefault(rule.getType());
      final GuardrailProvider<?> provider = typeVsProvider.get(type);
      if (provider == null) {
        LOG.warn("No guardrail provider registered for rule type '{}'.", type);
        continue;
      }
      final Guardrail created = createGuardrail(provider, rule);
      if (created != null) {
        stageVsGuardRails
            .computeIfAbsent(created.stage(), ignored -> new ArrayList<>())
            .add(created);
      }
    }
    final GuardrailErrorMode errorMode =
        config.getDefaultOnError() == null
            ? GuardrailErrorMode.FAIL_OPEN
            : config.getDefaultOnError();
    final GuardrailExecutionMode executionMode =
        config.getExecutionMode() == null ? GuardrailExecutionMode.SYNC : config.getExecutionMode();
    return new GuardrailPolicy(true, errorMode, executionMode, stageVsGuardRails);
  }

  private static List<GuardrailProvider<?>> collectProviders(
      final Instance<GuardrailProvider<?>> providers, final ClassLoader pluginLoader) {
    final List<GuardrailProvider<?>> allProviders = new ArrayList<>();
    for (final GuardrailProvider<?> provider : providers) {
      if (provider != null) {
        allProviders.add(provider);
      }
    }
    if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
      //noinspection rawtypes
      final ServiceLoader<GuardrailProvider> loader =
          ServiceLoader.load(GuardrailProvider.class, pluginLoader);
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
      final GuardrailProvider<?> provider, final GuardrailRule rule) {
    return ((GuardrailProvider<GuardrailRule>) provider).create(rule);
  }

  public record GuardrailPolicy(
      boolean enabled,
      GuardrailErrorMode errorMode,
      GuardrailExecutionMode executionMode,
      Map<GuardrailStage, List<Guardrail>> stageToGuardrails) {
    public static GuardrailPolicy disabled() {
      return new GuardrailPolicy(
          false, GuardrailErrorMode.FAIL_OPEN, GuardrailExecutionMode.SYNC, Map.of());
    }

    public List<Guardrail> guardrails(final GuardrailStage stage) {
      if (stage == null || stageToGuardrails == null) {
        return List.of();
      }
      return CollectionUtils.nullSafeList(stageToGuardrails.get(stage));
    }
  }
}
