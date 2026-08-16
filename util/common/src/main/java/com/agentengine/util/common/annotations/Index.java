package com.agentengine.util.common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an index on the annotated entity, created by {@link
 * com.agentengine.util.common.repository.IndexedRepository#ensureIndexes()}.
 *
 * <p>Indexes are declared next to the entity they belong to so that a query and the index serving
 * it are read together.
 */
@Target(ElementType.TYPE)
@Repeatable(Indexes.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {

  /** Index name. Defaults to one derived from the keys by the store. */
  String name() default "";

  /** Key specification, e.g. {@code "{'status': 1, 'dueAt': 1}"}. */
  String def();

  boolean unique() default false;

  /** TTL in seconds for the indexed date field. Ignored when negative. */
  long expireAfterSeconds() default -1;

  /** Restricts the index to matching documents, e.g. {@code "{'dedupeKey': {'$exists': true}}"}. */
  String partialFilterExpression() default "";

  /**
   * Drops the index instead of creating it. Set this to retire an index: deploy once so the drop is
   * applied, then delete the declaration.
   */
  boolean drop() default false;
}
