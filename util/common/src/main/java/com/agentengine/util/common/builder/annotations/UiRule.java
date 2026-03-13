package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a conditional builder rule for a field.
 *
 * <p>
 * Rules allow the builder definition to express conditions such as showing or
 * requiring a field only when another field has a matching value.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Repeatable(UiRules.class)
public @interface UiRule {
  /**
   * Builder effect to apply when the condition matches.
   *
   * @return rule effect
   */
  UiRuleEffect effect();

  /**
   * Field path or sibling field name to evaluate.
   *
   * @return controlling field identifier
   */
  String field();

  /**
   * Comparison operator used for the rule condition.
   *
   * @return condition operator
   */
  UiConditionOperator operator() default UiConditionOperator.EQ;

  /**
   * Values used by the condition operator.
   *
   * @return comparison values
   */
  String[] values();
}
