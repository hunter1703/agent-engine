package com.agentengine.util.mongo;

import com.agentengine.util.EncryptionService;
import com.agentengine.util.Secure;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.pojo.ClassModelBuilder;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.PropertyModelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SecurePropertyConvention implements Convention {

  private static final Logger LOG = LoggerFactory.getLogger(SecurePropertyConvention.class);

  private final Instance<EncryptionService> encryptionService;

  public SecurePropertyConvention(final Instance<EncryptionService> encryptionService) {
    this.encryptionService = encryptionService;
  }

  @Override
  public void apply(final ClassModelBuilder<?> classModelBuilder) {
    final Class<?> targetClass = classModelBuilder.getType();
    for (final PropertyModelBuilder<?> propertyModelBuilder :
        classModelBuilder.getPropertyModelBuilders()) {
      final String propertyName = propertyModelBuilder.getName();
      if (!isSecureProperty(targetClass, propertyName, propertyModelBuilder)) {
        continue;
      }

      final Class<?> propertyType = resolvePropertyType(targetClass, propertyName);
      if (!String.class.equals(propertyType)) {
        LOG.debug(
            "Secure annotation ignored for non-string field: {}.{}",
            targetClass.getSimpleName(),
            propertyName);
        continue;
      }

      @SuppressWarnings("unchecked")
      final PropertyModelBuilder<String> stringBuilder =
          (PropertyModelBuilder<String>) propertyModelBuilder;
      stringBuilder.codec(new SecureStringCodec(encryptionService));
    }
  }

  private static boolean isSecureProperty(
      final Class<?> targetClass,
      final String propertyName,
      final PropertyModelBuilder<?> propertyModelBuilder) {
    return hasSecureAnnotations(propertyModelBuilder)
        || hasSecureField(targetClass, propertyName)
        || hasSecureAccessor(targetClass, propertyName);
  }

  private static boolean hasSecureAnnotations(final PropertyModelBuilder<?> propertyModelBuilder) {
    if (propertyModelBuilder.getReadAnnotations() != null
        && propertyModelBuilder.getReadAnnotations().stream()
            .anyMatch(annotation -> annotation.annotationType().equals(Secure.class))) {
      return true;
    }
    return propertyModelBuilder.getWriteAnnotations() != null
        && propertyModelBuilder.getWriteAnnotations().stream()
            .anyMatch(annotation -> annotation.annotationType().equals(Secure.class));
  }

  private static boolean hasSecureField(final Class<?> targetClass, final String propertyName) {
    final Field field = findField(targetClass, propertyName);
    return field != null && field.isAnnotationPresent(Secure.class);
  }

  private static boolean hasSecureAccessor(final Class<?> targetClass, final String propertyName) {
    final String capitalizedName = capitalize(propertyName);
    final String getterName = "get" + capitalizedName;
    final String booleanGetterName = "is" + capitalizedName;
    final String setterName = "set" + capitalizedName;
    return Arrays.stream(targetClass.getMethods())
        .anyMatch(
            method ->
                method.isAnnotationPresent(Secure.class)
                    && isAccessorMatch(method, getterName, booleanGetterName, setterName));
  }

  private static boolean isAccessorMatch(
      final Method method,
      final String getterName,
      final String booleanGetterName,
      final String setterName) {
    if (method.getParameterCount() == 0) {
      return method.getName().equals(getterName) || method.getName().equals(booleanGetterName);
    }
    return method.getParameterCount() == 1 && method.getName().equals(setterName);
  }

  private static Class<?> resolvePropertyType(
      final Class<?> targetClass, final String propertyName) {
    final Field field = findField(targetClass, propertyName);
    if (field != null) {
      return field.getType();
    }
    final String capitalizedName = capitalize(propertyName);
    final String getterName = "get" + capitalizedName;
    final String booleanGetterName = "is" + capitalizedName;
    final String setterName = "set" + capitalizedName;
    for (final Method method : targetClass.getMethods()) {
      if (method.getParameterCount() == 0
          && (method.getName().equals(getterName)
              || method.getName().equals(booleanGetterName))) {
        return method.getReturnType();
      }
      if (method.getParameterCount() == 1 && method.getName().equals(setterName)) {
        return method.getParameterTypes()[0];
      }
    }
    return null;
  }

  private static Field findField(final Class<?> targetClass, final String propertyName) {
    for (Class<?> current = targetClass; current != null; current = current.getSuperclass()) {
      try {
        return current.getDeclaredField(propertyName);
      } catch (NoSuchFieldException ignored) {
      }
    }
    return null;
  }

  private static String capitalize(final String propertyName) {
    if (propertyName == null || propertyName.isEmpty()) {
      return propertyName;
    }
    return propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
  }

  private static final class SecureStringCodec implements Codec<String> {
    private static final Logger LOG = LoggerFactory.getLogger(SecureStringCodec.class);

    private final Instance<EncryptionService> encryptionService;
    private final AtomicReference<EncryptionService> cachedService = new AtomicReference<>();

    private SecureStringCodec(final Instance<EncryptionService> encryptionService) {
      this.encryptionService = encryptionService;
    }

    @Override
    public void encode(
        final BsonWriter writer, final String value, final EncoderContext encoderContext) {
      if (value == null) {
        writer.writeNull();
        return;
      }
      final EncryptionService resolvedService = resolveEncryptionService();
      if (resolvedService != null && resolvedService.isEncryptionEnabled()) {
        try {
          writer.writeString(resolvedService.encrypt(value));
        } catch (Exception e) {
          LOG.error("Failed to encrypt value; aborting write to prevent storing plaintext.", e);
          throw new RuntimeException("Failed to encrypt value", e);
        }
        return;
      }
      writer.writeString(value);
    }

    @Override
    public String decode(final BsonReader reader, final DecoderContext decoderContext) {
      if (reader.getCurrentBsonType() == BsonType.NULL) {
        reader.readNull();
        return null;
      }

      if (reader.getCurrentBsonType() != BsonType.STRING) {
        return null;
      }

      final String raw = reader.readString();
      final EncryptionService resolvedService = resolveEncryptionService();
      if (resolvedService == null || !resolvedService.isEncryptionEnabled()) {
        return raw;
      }
      try {
        return resolvedService.decrypt(raw);
      } catch (Exception e) {
        LOG.error("Failed to decrypt secure value; aborting read to prevent data corruption.", e);
        throw new RuntimeException("Failed to decrypt secure value", e);
      }
    }

    @Override
    public Class<String> getEncoderClass() {
      return String.class;
    }

    private EncryptionService resolveEncryptionService() {
      final EncryptionService cached = cachedService.get();
      if (cached != null) {
        return cached;
      }
      if (encryptionService == null || !encryptionService.isResolvable()) {
        return null;
      }
      final EncryptionService resolved = encryptionService.get();
      cachedService.compareAndSet(null, resolved);
      return cachedService.get();
    }
  }
}
