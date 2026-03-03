package com.agentengine.engine.repository.codec;

import com.agentengine.engine.api.beans.Secure;
import com.agentengine.engine.utils.EncryptionService;
import jakarta.enterprise.inject.Instance;
import org.bson.codecs.pojo.ClassModelBuilder;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.PropertyModelBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurePropertyConvention implements Convention {

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
      boolean isSecure = hasSecureAnnotations(propertyModelBuilder);
      if (!isSecure && hasSecureField(targetClass, propertyName)) {
        isSecure = true;
      }
      if (!isSecure && hasSecureAccessor(targetClass, propertyName)) {
        isSecure = true;
      }

      if (isSecure) {
        final Class<?> propertyType = resolvePropertyType(targetClass, propertyName);
        if (String.class.equals(propertyType)) {
          @SuppressWarnings("unchecked")
          final PropertyModelBuilder<String> stringBuilder =
              (PropertyModelBuilder<String>) propertyModelBuilder;
          stringBuilder.codec(new SecureStringCodec(encryptionService));
        } else {
          LOG.debug(
              "Secure annotation ignored for non-string field: {}.{}",
              targetClass.getSimpleName(),
              propertyName);
        }
      }
    }
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
}
