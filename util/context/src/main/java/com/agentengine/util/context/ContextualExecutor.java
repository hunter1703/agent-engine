package com.agentengine.util.context;

import com.google.common.util.concurrent.ForwardingExecutorService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ContextualExecutor extends ForwardingExecutorService {

  private final ExecutorService delegate;

  public ContextualExecutor(final ExecutorService delegate) {
    this.delegate = delegate;
  }

  @Override
  @NotNull
  protected ExecutorService delegate() {
    return delegate;
  }

  @Override
  public void execute(@NotNull final Runnable command) {
    delegate.execute(Context.bindCurrent(command));
  }

  @Override
  @NotNull
  public <T> Future<T> submit(@NotNull final Callable<T> task) {
    return delegate.submit(Context.bindCurrent(task));
  }

  @Override
  @NotNull
  public Future<?> submit(@NotNull final Runnable task) {
    return delegate.submit(Context.bindCurrent(task));
  }

  @Override
  @NotNull
  public <T> Future<T> submit(@NotNull final Runnable task, final T result) {
    return delegate.submit(Context.bindCurrent(task), result);
  }

  @Override
  @NotNull
  public <T> List<Future<T>> invokeAll(@NotNull final Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(bindAll(tasks));
  }

  @Override
  @NotNull
  public <T> List<Future<T>> invokeAll(
      @NotNull final Collection<? extends Callable<T>> tasks,
      final long timeout,
      @NotNull final TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(bindAll(tasks), timeout, unit);
  }

  @Override
  @NotNull
  public <T> T invokeAny(@NotNull final Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(bindAll(tasks));
  }

  @Override
  public <T> T invokeAny(
      @NotNull final Collection<? extends Callable<T>> tasks,
      final long timeout,
      @NotNull final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(bindAll(tasks), timeout, unit);
  }

  private static <T> List<Callable<T>> bindAll(final Collection<? extends Callable<T>> tasks) {
    final List<Callable<T>> bound = new ArrayList<>(tasks.size());
    for (final Callable<T> task : tasks) {
      bound.add(Context.bindCurrent(task));
    }
    return bound;
  }
}
