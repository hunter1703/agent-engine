package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;

/**
 * Told, synchronously and in the writer's context, about every committed write to one class of
 * entities through its repository. Any bean of this type is told; a listener that throws is logged
 * and does not fail the write.
 */
public interface EntityChangeListener<T extends BaseEntity> {

  /** The entity class whose repository's writes this listener is told about. */
  Class<T> entityClass();

  void onChange(EntityChange<T> change);
}
