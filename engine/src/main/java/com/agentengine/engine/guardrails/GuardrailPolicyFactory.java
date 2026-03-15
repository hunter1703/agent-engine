package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.GuardrailExecutionMode;
import com.agentengine.engine.api.beans.config.GuardrailRule;
import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import com.agentengine.engine.plugin.ServiceUtils;
import com.agentengine.util.common.CollectionUtils;
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

  private final Map<GuardrailRuleType, GuardrailProvider<? super GuardrailRule>> typeVsProvider;

  @Inject
  public GuardrailPolicyFactory(final @Any Instance<GuardrailProvider<?>> providerInstances) {
    this(ServiceUtils.loadServicesForType(providerInstances, GuardrailProvider.class));
  }

  public GuardrailPolicyFactory(final List<GuardrailProvider<?>> providers) {
    //noinspection unchecked
    this.typeVsProvider = CollectionUtils.transformToMap(providers, GuardrailProvider::type, provider -> (GuardrailProvider<? super GuardrailRule>) provider);
  }

  public GuardrailPolicy build(final GuardrailsConfig config) {
    if (config == null) {
      return GuardrailPolicy.disabled();
    }
    final Map<GuardrailStage, List<Guardrail>> stageVsGuardRails = new EnumMap<>(GuardrailStage.class);

    for (final GuardrailRule rule : CollectionUtils.nullSafeList(config.getRules())) {
      if (!rule.isEnabled()) {
        continue;
      }
      final GuardrailRuleType type = GuardrailRuleType.valueOfOrDefault(rule.getType());
      final GuardrailProvider<? super GuardrailRule> provider = typeVsProvider.get(type);
      if (provider == null) {
        LOG.warn("No guardrail provider registered for rule type '{}'.", type);
        continue;
      }
      final Guardrail created = provider.create(rule);
      if (created != null) {
        stageVsGuardRails.computeIfAbsent(created.stage(), ignored -> new ArrayList<>()).add(created);
      }
    }
    final GuardrailErrorMode errorMode = config.defaultOnErrorEnum() == GuardrailErrorMode.UNKNOWN
        ? GuardrailErrorMode.FAIL_OPEN
        : config.defaultOnErrorEnum();
    final GuardrailExecutionMode executionMode = config.executionModeEnum() == GuardrailExecutionMode.UNKNOWN
        ? GuardrailExecutionMode.SYNC
        : config.executionModeEnum();
    return new GuardrailPolicy(config.isEnabled(), errorMode, executionMode, stageVsGuardRails);
  }

  public record GuardrailPolicy(boolean enabled, GuardrailErrorMode errorMode, GuardrailExecutionMode executionMode,
      Map<GuardrailStage, List<Guardrail>> stageToGuardrails) {
    public static GuardrailPolicy disabled() {
      return new GuardrailPolicy(false, GuardrailErrorMode.FAIL_OPEN, GuardrailExecutionMode.SYNC, Map.of());
    }

    public List<Guardrail> guardrails(final GuardrailStage stage) {
      if (stage == null || stageToGuardrails == null) {
        return List.of();
      }
      return CollectionUtils.nullSafeList(stageToGuardrails.get(stage));
    }
  }
}
