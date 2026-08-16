package com.agentengine.util.common.builder.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the layout structure for a configuration class, defining the order of steps and sections
 * within the UI.
 *
 * <p>This annotation should be placed on the root configuration class to explicitly define how
 * steps and sections are ordered in the multi-step form wizard.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;UiLayout(
 *     steps = {
 *         &#64;UiStep(id = "identity", label = "Identity", order = 0),
 *         &#64;UiStep(id = "model", label = "Model Configuration", order = 1,
 *             sections = {
 *                 &#64;UiSection(id = "model", label = "Model", order = 0),
 *                 &#64;UiSection(id = "context", label = "Context", order = 1)
 *             }),
 *         &#64;UiStep(id = "guardrails", label = "Guardrails", order = 2),
 *         &#64;UiStep(id = "runtime", label = "Runtime", order = 3)
 *     }
 * )
 * public class BaseAgentConfig { ... }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface UiSteps {
  /**
   * Ordered array of steps that define the multi-step wizard structure.
   *
   * @return array of step definitions
   */
  UiStep[] steps();
}
