package com.agentengine.agent.infra.guardrails;

import com.agentengine.agent.infra.ServiceUtils;
import com.agentengine.util.agents.beans.config.GuardrailErrorMode;
import com.agentengine.util.agents.beans.config.GuardrailRule;
import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.GuardrailStage;
import com.agentengine.util.agents.beans.config.GuardrailsConfig;
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

    private final Map<GuardrailRuleType, GuardrailFactory<? super GuardrailRule>> typeVsProvider;

    @Inject
    public GuardrailPolicyFactory(final @Any Instance<GuardrailFactory<?>> providerInstances) {
        this(ServiceUtils.loadServicesForType(providerInstances, GuardrailFactory.class));
    }

    public GuardrailPolicyFactory(final List<GuardrailFactory<?>> providers) {
        // noinspection unchecked
        this.typeVsProvider = CollectionUtils.transformToMap(
                providers, GuardrailFactory::type, provider -> (GuardrailFactory<? super GuardrailRule>) provider);
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
            final GuardrailFactory<? super GuardrailRule> provider = typeVsProvider.get(type);
            if (provider == null) {
                LOG.warn("No guardrail provider registered for rule type '{}'.", type);
                continue;
            }
            final Guardrail created = provider.create(rule);
            if (created != null) {
                stageVsGuardRails
                        .computeIfAbsent(created.stage(), ignored -> new ArrayList<>())
                        .add(created);
            }
        }
        final GuardrailErrorMode errorMode = config.defaultOnErrorEnum() == GuardrailErrorMode.UNKNOWN
                ? GuardrailErrorMode.FAIL_OPEN
                : config.defaultOnErrorEnum();
        return new GuardrailPolicy(config.isEnabled(), errorMode, stageVsGuardRails);
    }

    public record GuardrailPolicy(
            boolean enabled, GuardrailErrorMode errorMode, Map<GuardrailStage, List<Guardrail>> stageToGuardrails) {
        public static GuardrailPolicy disabled() {
            return new GuardrailPolicy(false, GuardrailErrorMode.FAIL_OPEN, Map.of());
        }

        public List<Guardrail> guardrails(final GuardrailStage stage) {
            if (stage == null || stageToGuardrails == null) {
                return List.of();
            }
            return CollectionUtils.nullSafeList(stageToGuardrails.get(stage));
        }
    }
}
