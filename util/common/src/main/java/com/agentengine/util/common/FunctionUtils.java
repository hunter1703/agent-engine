package com.agentengine.util.common;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Builds objects that look and behave exactly like a real {@code T} to every caller, but hold onto
 * their original JSON bytes instead of actually deserializing them -- until something actually
 * calls a method on one, at which point it deserializes for real (once, memoized) and delegates.
 *
 * <p>This turns "should this response be raw or fully deserialized" from a decision a caller has to
 * make up front into something that resolves itself: a caller that only ever forwards the object
 * untouched (e.g. a REST handler proxying a microservice response straight through) never pays
 * Jackson's cost at all; a caller that reads a field pays exactly what it would have paid anyway,
 * one call frame later. Every {@code T}-typed caller in the codebase gets this for free, with no
 * interface changes and no per-call opt-in, because the returned object really is a {@code T} --
 * {@link ByteBuddy} generates an actual subclass of it, so it satisfies every cast a normal {@code
 * T} would.
 */
public final class FunctionUtils {
  private static final ConcurrentMap<Class<?>, Class<?>> GENERATED_RAW_STUBS =
      new ConcurrentHashMap<>();
  private static final String HANDLER_FIELD_NAME = "$rawHandler";

  private FunctionUtils() {}

  public static boolean canBuildRawStub(final Class<?> rawClass) {
    if (rawClass.isPrimitive() || rawClass.isArray() || Modifier.isFinal(rawClass.getModifiers())) {
      return false;
    }
    if (rawClass.isInterface()) {
      return true;
    }
    for (final Constructor<?> constructor : rawClass.getDeclaredConstructors()) {
      if (constructor.getParameterCount() == 0) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  public static <T> T buildRawStub(
      final Type declaredType,
      final Class<T> rawClass,
      final JsonCodec codec,
      final byte[] rawBytes) {
    final Class<?> generatedType =
        GENERATED_RAW_STUBS.computeIfAbsent(rawClass, FunctionUtils::generateStub);
    try {
      final Constructor<?> constructor = generatedType.getDeclaredConstructor();
      constructor.setAccessible(true);
      final Object instance = constructor.newInstance();

      final Field handlerField = generatedType.getDeclaredField(HANDLER_FIELD_NAME);
      handlerField.setAccessible(true);
      handlerField.set(instance, new StubHandler(rawBytes, declaredType, codec));
      return (T) instance;
    } catch (final ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Failed to build a raw-passthrough stub for " + rawClass.getName(), exception);
    }
  }

  private static Class<?> generateStub(final Class<?> targetType) {
    try (DynamicType.Unloaded<?> unloaded =
        new ByteBuddy()
            // IMITATE_SUPER_CLASS (not the default IMITATE_SUPER_CLASS_PUBLIC): several of our
            // DTOs use a protected no-arg constructor (e.g. BaseAgentConfig), which the
            // public-only default would silently skip, leaving the generated subclass with no
            // usable constructor at all.
            .subclass(targetType, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
            .implement(RawBytes.class)
            .defineField(HANDLER_FIELD_NAME, InvocationHandler.class, Visibility.PRIVATE)
            // Every method -- including Object's own (toString/equals/hashCode) -- routes through
            // the same handler, so nothing observes an unpopulated instance: the first touch of
            // anything triggers real deserialization, same as calling the real method would have
            // anyway.
            .method(ElementMatchers.any())
            .intercept(InvocationHandlerAdapter.toField(HANDLER_FIELD_NAME))
            .make()) {
      // JDK-owned targets (Map, List, ...) have a null classloader (bootstrap), which INJECTION
      // can't define into -- fall back to ours; the generated class only needs to see the target
      // type's own (public API) members, which are visible from any loader regardless.
      return unloaded
          .load(
              targetType.getClassLoader() != null
                  ? targetType.getClassLoader()
                  : FunctionUtils.class.getClassLoader(),
              ClassLoadingStrategy.Default.INJECTION)
          .getLoaded();
    }
  }

  private static final class StubHandler implements InvocationHandler {
    private final byte[] rawBytes;
    private final LazyLoader<Object> delegate;

    private StubHandler(final byte[] rawBytes, final Type declaredType, final JsonCodec codec) {
      this.rawBytes = rawBytes;
      this.delegate =
          new LazyLoader<>(
              () -> codec.deserialize(new ByteArrayInputStream(rawBytes), declaredType));
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
        throws Throwable {
      if (isRawBytesAccessor(method)) {
        return rawBytes;
      }
      try {
        return method.invoke(delegate.get(), args);
      } catch (final InvocationTargetException exception) {
        throw exception.getCause();
      }
    }

    private static boolean isRawBytesAccessor(final Method method) {
      return "bytes".equals(method.getName()) && method.getParameterCount() == 0;
    }
  }
}
