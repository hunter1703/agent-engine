package com.agentengine.util.common.utils;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.context.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sets the contextual fields of an entity about to be written — the ones a repository maintains
 * rather than the entity's author — leaving which fields those are to the entity.
 */
public final class EntityUtils {

  private EntityUtils() {}

  /** Readies a new entity to be stored: version 0, created now by the current caller. */
  public static void prepareNew(final BaseEntity entity) {
    entity.cleanContextualFields();
    stamp(entity);
  }

  /**
   * Readies {@code entity} to be written whole over {@code existing}: carries its contextual fields
   * over and counts a new version. With no {@code existing}, readies it as new.
   */
  public static void prepareReplacement(final BaseEntity entity, final BaseEntity existing) {
    if (existing == null) {
      prepareNew(entity);
      return;
    }
    entity.cleanContextualFields();
    entity.copyContextualFieldsFrom(existing);
    entity.setVersion(existing.getVersion() + 1);
    stamp(entity);
  }

  /**
   * {@code update} without any operation on {@code contextualFields} or a field within one, and,
   * when anything is left, stamped with the write time and a new version.
   */
  public static Update prepareUpdate(final Update update, final Set<String> contextualFields) {
    final List<Operation> operations = new ArrayList<>();
    for (final Operation operation : update.operations()) {
      if (!isWithin(operation.field(), contextualFields)) {
        operations.add(operation);
      }
    }
    if (!operations.isEmpty()) {
      operations.add(Operation.set(BaseEntity.FIELD_UPDATED_TIME, System.currentTimeMillis()));
      operations.add(Operation.inc(BaseEntity.FIELD_VERSION, 1L));
    }
    return new Update(operations);
  }

  private static boolean isWithin(final String field, final Set<String> fields) {
    if (fields.contains(field)) {
      return true;
    }
    final int dot = field.indexOf('.');
    return dot > 0 && fields.contains(field.substring(0, dot));
  }

  /**
   * Sets the creator, when none is set yet — the principal the work is attributed to, or the
   * caller's name for the system — and the creation and update times.
   */
  private static void stamp(final BaseEntity entity) {
    if (StringUtils.isBlank(entity.getCreatedBy())) {
      entity.setCreatedBy(
          Context.current()
              .map(
                  context ->
                      context.principal().map(Object::toString).orElse(context.caller().toString()))
              .orElse(null));
    }
    final long now = System.currentTimeMillis();
    if (entity.getCreatedTime() == 0) {
      entity.setCreatedTime(now);
    }
    entity.setUpdatedTime(now);
  }
}
