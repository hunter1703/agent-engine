package com.agentengine.util.scripts.templated;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.EnvUtils;
import com.agentengine.util.common.ThreadUtils;
import com.agentengine.util.scripts.exception.TemplateException;
import groovy.lang.Binding;
import groovy.lang.Script;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GroovyTemplate<T> implements Template<T> {

  private static final Duration EVALUATION_TIMEOUT = Duration.ofSeconds(10);

  // CPU-bound script execution never yields its carrier, so this runs on its own bounded platform
  // pool rather than virtual threads — those all share one JVM-wide carrier scheduler, and a burst
  // of script evaluations would otherwise starve unrelated I/O-bound virtual-thread work.
  private static final int MIN_EVALUATION_THREADS = 8;

  private static final ExecutorService EVALUATION_EXECUTOR =
      ThreadUtils.newFixedThreadExecutor(
          "groovy-template-",
          Math.max(MIN_EVALUATION_THREADS, Runtime.getRuntime().availableProcessors()));

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
    final Map<String, Object> bindingVariables = CollectionUtils.nullSafeMutableMap(parameters);
    bindingVariables.put("env", EnvUtils.getAll());

    final Script script = constructor.newInstance();
    script.setBinding(new Binding(bindingVariables));
    return (T) script.run();
  }
}
