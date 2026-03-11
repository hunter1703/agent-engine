package com.agentengine.engine.api.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.google.adk.JsonBaseModel;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class Utils {
  public static final ObjectMapper OBJECT_MAPPER = JsonBaseModel.getMapper();

  private Utils() {}

  public static boolean isSimpleType(final Class<?> type) {
    if (type == null) {
      return false;
    }
    return type == Integer.class
        || type == int.class
        || type == Long.class
        || type == long.class
        || type == Double.class
        || type == double.class
        || type == Float.class
        || type == float.class
        || type == Boolean.class
        || type == boolean.class
        || type == String.class
        || type.isEnum();
  }

  public static Object castValue(final Object rawValue, final Class<?> targetType) {
    if (rawValue == null) {
      return null;
    }
    if (targetType == Integer.class || targetType == int.class) {
      if (rawValue instanceof Integer integerValue) {
        return integerValue;
      }
    } else if (targetType == Long.class || targetType == long.class) {
      if (rawValue instanceof Long || rawValue instanceof Integer) {
        return rawValue;
      }
    } else if (targetType == Double.class || targetType == double.class) {
      if (rawValue instanceof Double doubleValue) {
        return doubleValue;
      }
      if (rawValue instanceof Float floatValue) {
        return floatValue.doubleValue();
      }
      if (rawValue instanceof Integer integerValue) {
        return integerValue.doubleValue();
      }
      if (rawValue instanceof Long longValue) {
        return longValue.doubleValue();
      }
    } else if (targetType == Float.class || targetType == float.class) {
      if (rawValue instanceof Double doubleValue) {
        return doubleValue.floatValue();
      }
      if (rawValue instanceof Float floatValue) {
        return floatValue.floatValue();
      }
      if (rawValue instanceof Integer integerValue) {
        return integerValue.floatValue();
      }
      if (rawValue instanceof Long longValue) {
        return longValue.floatValue();
      }
    } else if (targetType == Boolean.class || targetType == boolean.class) {
      if (rawValue instanceof Boolean) {
        return rawValue;
      }
    } else if (targetType == String.class) {
      if (rawValue instanceof String) {
        return rawValue;
      }
    }
    return OBJECT_MAPPER.convertValue(rawValue, targetType);
  }

  public static <T> T convertValue(final Object rawValue, final Class<T> targetType) {
    return OBJECT_MAPPER.convertValue(rawValue, targetType);
  }

  public static <T> T convertValue(final Object rawValue, final TypeReference<T> typeReference) {
    return OBJECT_MAPPER.convertValue(rawValue, typeReference);
  }

  public static Object convertValue(final Object rawValue, final JavaType targetType) {
    return OBJECT_MAPPER.convertValue(rawValue, targetType);
  }

  public static Class<?> getTypeClass(final Type type, final String paramName) {
    if (type instanceof Class<?> clazz) {
      return clazz;
    } else if (type instanceof ParameterizedType parameterizedType) {
      return (Class<?>) parameterizedType.getRawType();
    }
    final String label = paramName == null ? "" : " for '" + paramName + "'";
    throw new IllegalArgumentException(
        String.format("Unsupported parameterized type %s%s", type, label));
  }

  public static Method resolveMethod(final Class<?> toolClass, final String methodName) {
    for (Method method : toolClass.getMethods()) {
      if (method.getName().equals(methodName)) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new IllegalArgumentException(
        String.format("Method %s not found in class %s.", methodName, toolClass.getName()));
  }

  public static AnnotatedMember resolveMember(final BeanPropertyDefinition property) {
    if (property.getPrimaryMember() != null) {
      return property.getPrimaryMember();
    }
    if (property.getGetter() != null) {
      return property.getGetter();
    }
    if (property.getField() != null) {
      return property.getField();
    }
    return property.getSetter();
  }

  public static <T extends Annotation> T findAnnotation(
      final BeanPropertyDefinition property, final Class<T> annotationClass) {
    if (property == null) {
      return null;
    }
    if (property.getPrimaryMember() != null) {
      T annotation = property.getPrimaryMember().getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }
    if (property.getGetter() != null) {
      T annotation = property.getGetter().getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }
    if (property.getField() != null) {
      T annotation = property.getField().getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }
    if (property.getSetter() != null) {
      return property.getSetter().getAnnotation(annotationClass);
    }
    return null;
  }
}
