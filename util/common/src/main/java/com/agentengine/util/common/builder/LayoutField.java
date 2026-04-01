package com.agentengine.util.common.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutField(
        String label,
        String widget,
        String step,
        String section,
        Integer order,
        LayoutAccessPolicy access,
        Boolean collection,
        Boolean multiline,
        Integer rows,
        String numberType,
        Boolean advanced,
        List<Object> options,
        List<LayoutFieldRule> rules,
        LayoutLookup lookup,
        List<LayoutPreset> presets,
        LayoutDynamicSchema dynamicSchema,
        Boolean sensitive,
        String currentAccess) {

    public LayoutField withCurrentAccess(final String accessLevel) {
        return new LayoutField(
                label,
                widget,
                step,
                section,
                order,
                access,
                collection,
                multiline,
                rows,
                numberType,
                advanced,
                options,
                rules,
                lookup,
                presets,
                dynamicSchema,
                sensitive,
                accessLevel);
    }

    public LayoutField withPresets(final List<LayoutPreset> fieldPresets) {
        return new LayoutField(
                label,
                widget,
                step,
                section,
                order,
                access,
                collection,
                multiline,
                rows,
                numberType,
                advanced,
                options,
                rules,
                lookup,
                fieldPresets == null || fieldPresets.isEmpty() ? null : List.copyOf(fieldPresets),
                dynamicSchema,
                sensitive,
                currentAccess);
    }
}
