package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a section within a step in the multi-step form wizard.
 *
 * <p>A section represents a logical grouping of related fields within a step. Sections are only
 * shown as separate groups if a step contains multiple sections.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface UiSection {
    /**
     * Unique identifier for the section within its step.
     *
     * <p>This ID must match the section IDs used in {@link UiField#section()}.
     *
     * @return section identifier
     */
    String id();

    /**
     * Human-readable label for the section.
     *
     * <p>This label is displayed as a section header if the step has multiple sections.
     *
     * @return section label
     */
    String label();

    /**
     * Optional description of the section.
     *
     * @return section description
     */
    String description() default "";

    /**
     * Sort order for the section within its step.
     *
     * <p>Sections are displayed in ascending order. Lower values appear first.
     *
     * @return sort order
     */
    int order();
}
