package com.agentengine.interfaces.rest.support;

import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;

public final class HandlerInstance implements Instance<AgentRequestHandler<?>> {
  private final List<AgentRequestHandler<?>> handlers;

  public HandlerInstance(final List<AgentRequestHandler<?>> handlers) {
    this.handlers = handlers;
  }

  @Override
  public @NotNull Iterator<AgentRequestHandler<?>> iterator() {
    return handlers.iterator();
  }

  @Override
  public AgentRequestHandler<?> get() {
    return handlers.getFirst();
  }

  @Override
  public boolean isUnsatisfied() {
    return handlers.isEmpty();
  }

  @Override
  public boolean isAmbiguous() {
    return handlers.size() > 1;
  }

  @Override
  public void destroy(final AgentRequestHandler instance) {
    // No-op for tests
  }

  @Override
  public Handle<AgentRequestHandler<?>> getHandle() {
    throw new UnsupportedOperationException("Not used in tests");
  }

  @Override
  public Iterable<? extends Handle<AgentRequestHandler<?>>> handles() {
    throw new UnsupportedOperationException("Not used in tests");
  }

  @Override
  public Instance<AgentRequestHandler<?>> select(final Annotation... qualifiers) {
    throw new UnsupportedOperationException("Not used in tests");
  }

  @Override
  public <U extends AgentRequestHandler<?>> Instance<U> select(final Class<U> subtype, final Annotation... qualifiers) {
    throw new UnsupportedOperationException("Not used in tests");
  }

  @Override
  public <U extends AgentRequestHandler<?>> Instance<U> select(final TypeLiteral<U> subtype,
      final Annotation... qualifiers) {
    throw new UnsupportedOperationException("Not used in tests");
  }
}
