package com.agentengine.agent.infra;

import com.agentengine.util.common.Utils;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class ServiceUtils {

  private ServiceUtils() {}

  public static <T> List<T> loadServicesForType(
      final Instance<? extends T> instances, final Type type) {
    return loadServices(instances, Utils.getClass(type));
  }

  public static <T> List<T> loadServices(
      final Instance<? extends T> instances, final Class<?> clazz) {
    final List<T> allProviders = new ArrayList<>();
    for (final T provider : instances) {
      allProviders.add(provider);
    }
    return allProviders;
  }
}
