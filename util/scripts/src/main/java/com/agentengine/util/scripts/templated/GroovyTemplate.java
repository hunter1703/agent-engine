package com.agentengine.util.scripts.templated;

import com.agentengine.util.scripts.exception.TemplateException;
import groovy.lang.Binding;
import groovy.lang.Script;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GroovyTemplate<T> implements Template<T> {

  private static final Duration EVALUATION_TIMEOUT = Duration.ofSeconds(10);
  private static final ExecutorService EVALUATION_EXECUTOR =
      Executors.newVirtualThreadPerTaskExecutor();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(EVALUATION_EXECUTOR::shutdownNow));
  }

  private final String templateStr;
  private final Constructor<? extends Script> constructor;

  public GroovyTemplate(String templateStr, Constructor<? extends Script> constructor) {
    this.templateStr = templateStr;
    this.constructor = constructor;
  }

  @Override
  public T getValue(Map<String, Object> parameters) {
    final Future<T> task = EVALUATION_EXECUTOR.submit(() -> evaluate(parameters));
    try {
      return task.get(EVALUATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
      throw new TemplateException("groovy template evaluation timed out: " + templateStr, e);
    } catch (ExecutionException e) {
      throw new TemplateException(
          "failed to evaluate groovy template: " + templateStr, e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TemplateException("groovy template evaluation interrupted: " + templateStr, e);
    }
  }

  @SuppressWarnings("unchecked")
  private T evaluate(Map<String, Object> parameters) throws Exception {
    final Map<String, Object> bindingVariables = new HashMap<>();
    bindingVariables.put("input", parameters);
    bindingVariables.put("env", System.getenv());

    final Script script = constructor.newInstance();
    script.setBinding(new Binding(bindingVariables));
    return (T) script.run();
  }
}
