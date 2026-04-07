package com.agentengine.connectors.core.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.connectors.core.runtime.RequestContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultTemplateResolverTest {

    private final DefaultTemplateResolver resolver = new DefaultTemplateResolver(new GroovySandboxEvaluator());

    @Test
    void resolvesStaticAndInlineValues() {
        final RequestContext context = RequestContext.empty().withVars(Map.of("env", "prod", "id", 42));

        final ResolvedValue staticValue = resolver.resolve("hello", context, TemplateResolutionOptions.strict());
        final ResolvedValue inlineValue =
                resolver.resolve("https://${env}.api.com/{{ id }}", context, TemplateResolutionOptions.strict());

        assertThat(staticValue.value()).isEqualTo("hello");
        assertThat(inlineValue.value()).isEqualTo("https://prod.api.com/42");
    }

    @Test
    void resolvesMapsAndListsRecursively() {
        final RequestContext context = RequestContext.empty().withVars(Map.of("a", "A", "b", "B"));
        final Map<String, Object> mapTemplate = Map.of("first", TemplateDirective.expr("a"));
        final List<Object> listTemplate = List.of(TemplateDirective.expr("a"), TemplateDirective.expr("b"));

        final ResolvedValue mapValue = resolver.resolve(mapTemplate, context, TemplateResolutionOptions.strict());
        final ResolvedValue listValue = resolver.resolve(listTemplate, context, TemplateResolutionOptions.strict());

        assertThat(mapValue.value()).isEqualTo(Map.of("first", "A"));
        assertThat(listValue.value()).isEqualTo(List.of("A", "B"));
    }

    @Test
    void optionalDirectiveOmitsMissingValues() {
        final RequestContext context = RequestContext.empty();
        final Map<String, Object> template = Map.of("always", "x", "optional", TemplateDirective.optional("missing"));

        final ResolvedValue value = resolver.resolve(template, context, TemplateResolutionOptions.strict());

        assertThat(value.value()).isEqualTo(Map.of("always", "x"));
    }

    @Test
    void includeIfDirectiveControlsPresence() {
        final RequestContext trueContext = RequestContext.empty().withVars(Map.of("enabled", true));
        final RequestContext falseContext = RequestContext.empty().withVars(Map.of("enabled", false));
        final Object directive = TemplateDirective.includeIf("enabled", Map.of("name", "test"));

        final ResolvedValue included = resolver.resolve(directive, trueContext, TemplateResolutionOptions.strict());
        final ResolvedValue omitted = resolver.resolve(directive, falseContext, TemplateResolutionOptions.strict());

        assertThat(included.value()).isEqualTo(Map.of("name", "test"));
        assertThat(omitted.status()).isEqualTo(ResolvedValueStatus.OMITTED);
    }

    @Test
    void strictModeThrowsForMissingVariables() {
        assertThatThrownBy(() ->
                        resolver.resolve("{{ missing }}", RequestContext.empty(), TemplateResolutionOptions.strict()))
                .isInstanceOf(TemplateResolutionException.class);
    }

    @Test
    void lenientModeReturnsUnresolvedForMissingVariables() {
        final ResolvedValue value =
                resolver.resolve("{{ missing }}", RequestContext.empty(), new TemplateResolutionOptions(false, false));

        assertThat(value.status()).isEqualTo(ResolvedValueStatus.UNRESOLVED);
    }
}
