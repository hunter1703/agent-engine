package com.agentengine.util.common;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Allows every subtype. Only safe where both ends of the wire format are our own services (gRPC,
 * Pekko, Mongo) — never reuse this for a mapper that deserializes external input.
 */
public final class AllowAllPolymorphicTypeValidator extends PolymorphicTypeValidator.Base {

  public static final PolymorphicTypeValidator INSTANCE = new AllowAllPolymorphicTypeValidator();

  private AllowAllPolymorphicTypeValidator() {}

  @Override
  public Validity validateSubClassName(
      final MapperConfig<?> config, final JavaType baseType, final String subClassName) {
    return Validity.ALLOWED;
  }
}
