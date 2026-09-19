package com.agentengine.util.common.context;

import com.google.common.util.concurrent.ForwardingExecutorService;
import jakarta.validation.constraints.NotNull;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
}
