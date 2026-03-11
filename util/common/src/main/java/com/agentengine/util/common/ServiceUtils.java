package com.agentengine.util.common;

import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class ServiceUtils {

    private ServiceUtils() {
    }

    public static <T> List<T> loadServicesForType(final Instance<? extends T> instances, final Type type) {
        return loadServices(instances, Utils.getClass(type));
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> loadServices(final Instance<? extends T> instances, final Class<?> clazz) {
        final List<T> allProviders = new ArrayList<>();
        for (final T provider : instances) {
            allProviders.add(provider);
        }
        final ClassLoader pluginLoader = PluginLoader.getClassLoader();
        if (pluginLoader != Thread.currentThread().getContextClassLoader()) {
            final ServiceLoader<?> loader = ServiceLoader.load((Class<?>) clazz, pluginLoader);
            for (final Object provider : loader) {
                allProviders.add((T) provider);
            }
        }
        return allProviders;
    }
}
