package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a complex conditional builder rule using a JSONLogic expression.
 *
 * <p>Use this annotation when the condition cannot be expressed with {@link UiRule}. The {@code
 * value} must be a valid JSONLogic JSON string that is parsed into a rule expression at
 * definition-generation time.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface UiExpression {
  /** Builder effect to apply when the expression evaluates to true. */
  UiRuleEffect effect();

  /** JSONLogic expression as a JSON string. */
  String value();
}
