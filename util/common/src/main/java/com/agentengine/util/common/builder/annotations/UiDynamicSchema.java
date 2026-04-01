package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field whose schema is resolved dynamically at runtime via the {@code /schemas} endpoint.
 * The UI will POST to the schema resolver using the provided params to obtain the field's schema
 * before rendering.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiDynamicSchema {
    /** The asset type passed to the schema resolver. */
    String assetType();

    /**
     * JSONPath expression to resolve the asset ID from the current item context. Example: {@code
     * "$item.toolName"} resolves to the {@code toolName} of the current list item.
     */
    String assetIdExpr() default "";

    /**
     * JSONPath expression to resolve the context (parent entity) ID. Example: {@code "$.id"} resolves
     * to the ID of the root entity being edited.
     */
    String contextIdExpr() default "";
}
