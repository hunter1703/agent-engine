package com.agentengine.connectors.core.runtime;

import com.agentengine.connectors.core.auth.AuthStrategyRegistry;
import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.PaginationType;
import com.agentengine.connectors.core.config.RetryBackoffType;
import com.agentengine.connectors.core.http.HttpRequestData;
import com.agentengine.connectors.core.http.HttpResponseData;
import com.agentengine.connectors.core.http.HttpTransport;
import com.agentengine.connectors.core.http.OkHttpTransport;
import com.agentengine.connectors.core.pagination.PaginationDirective;
import com.agentengine.connectors.core.pagination.PaginationState;
import com.agentengine.connectors.core.pagination.PaginationStrategy;
import com.agentengine.connectors.core.pagination.PaginationStrategyRegistry;
import com.agentengine.connectors.core.response.DefaultErrorClassifier;
import com.agentengine.connectors.core.response.DefaultResponseMapper;
import com.agentengine.connectors.core.response.JsonPathResponseExtractor;
import com.agentengine.connectors.core.response.ResponseExtractor;
import com.agentengine.connectors.core.template.DefaultTemplateResolver;
import com.agentengine.connectors.core.template.GroovySandboxEvaluator;
import com.agentengine.connectors.core.validation.ConnectorConfigValidator;
import com.agentengine.connectors.core.validation.DefaultConnectorConfigValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class DefaultConnectorExecutor implements ConnectorExecutor {

  private final ConnectorConfigValidator validator;
  private final RequestMaterializer materializer;
  private final HttpTransport transport;
  private final PaginationStrategyRegistry paginationRegistry;
  private final DefaultResponseMapper responseMapper;

  @Inject
  public DefaultConnectorExecutor() {
    this(
        new DefaultConnectorConfigValidator(),
        new RequestMaterializer(
            new DefaultTemplateResolver(new GroovySandboxEvaluator()), new AuthStrategyRegistry()),
        new OkHttpTransport(),
        new PaginationStrategyRegistry(),
        createDefaultResponseMapper());
  }

  public DefaultConnectorExecutor(
      final ConnectorConfigValidator connectorConfigValidator,
      final RequestMaterializer requestMaterializer,
      final HttpTransport httpTransport,
      final PaginationStrategyRegistry paginationStrategyRegistry,
      final DefaultResponseMapper responseMapper) {
    this.validator = connectorConfigValidator;
    this.materializer = requestMaterializer;
    this.transport = httpTransport;
    this.paginationRegistry = paginationStrategyRegistry;
    this.responseMapper = responseMapper;
  }

  @Override
  public ConnectorExecutionResult executeOnce(
      final ConnectorDefinition definition, final RequestContext context) {
    validator.validateOrThrow(definition);
    return executeWithRetry(definition, context, PaginationDirective.empty());
  }

  @Override
  public PaginatedExecutionResult executeAllPages(
      final ConnectorDefinition definition, final RequestContext context) {
    validator.validateOrThrow(definition);

    final PaginationType paginationType = definition.pagination().typeEnum();
    if (paginationType == PaginationType.NONE) {
      final ConnectorExecutionResult singleResult = executeOnce(definition, context);
      return new PaginatedExecutionResult(
          List.of(singleResult), asItems(singleResult.mappedData()), false, 1);
    }

    final PaginationStrategy paginationStrategy =
        paginationRegistry.resolve(definition.pagination().typeEnum());
    PaginationState paginationState = paginationStrategy.initialState(definition.pagination());

    final List<ConnectorExecutionResult> pageResults = new ArrayList<>();
    final List<Object> aggregatedItems = new ArrayList<>();
    RequestContext currentContext = context;

    int guard = 0;
    while (!paginationState.done() && guard < definition.pagination().maxPages()) {
      final PaginationDirective directive =
          paginationStrategy.buildRequest(definition.pagination(), paginationState);
      final ConnectorExecutionResult pageResult =
          executeWithRetry(definition, currentContext, directive);

      pageResults.add(pageResult);
      aggregatedItems.addAll(asItems(pageResult.mappedData()));

      paginationState =
          paginationStrategy.updateState(
              definition.pagination(),
              paginationState,
              pageResult.rawResponseBody(),
              pageResult.mappedData());
      currentContext = currentContext.withPrevious(toPreviousMap(pageResult));
      guard++;
    }

    final boolean truncated = !paginationState.done();
    return new PaginatedExecutionResult(
        pageResults, aggregatedItems, truncated, pageResults.size());
  }

  private ConnectorExecutionResult executeWithRetry(
      final ConnectorDefinition definition,
      final RequestContext context,
      final PaginationDirective paginationDirective) {
    final boolean retriesEnabled = definition.retryPolicy().enabled();
    final int maxAttempts =
        retriesEnabled ? Math.max(1, definition.retryPolicy().maxAttempts()) : 1;
    long currentDelayMs = definition.retryPolicy().initialDelayMs();

    RuntimeException lastException = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      final HttpRequestData requestData =
          materializer.materialize(definition, context, paginationDirective);
      try {
        final HttpResponseData responseData = transport.execute(requestData);
        final ConnectorExecutionResult mappedResult =
            responseMapper.map(
                definition, responseData, requestData.url(), requestData.method().name());

        if (mappedResult.success()
            || !shouldRetry(definition, mappedResult, attempt, maxAttempts)) {
          return mappedResult;
        }
      } catch (RuntimeException ex) {
        lastException = ex;
        if (!retriesEnabled || attempt >= maxAttempts) {
          throw new ConnectorExecutionException("Connector execution failed", ex);
        }
      }

      sleep(currentDelayMs);
      currentDelayMs = nextDelay(definition, currentDelayMs);
    }

    if (lastException != null) {
      throw new ConnectorExecutionException("Connector execution failed", lastException);
    }

    throw new ConnectorExecutionException("Connector execution failed without result");
  }

  private static boolean shouldRetry(
      final ConnectorDefinition definition,
      final ConnectorExecutionResult result,
      final int attempt,
      final int maxAttempts) {
    if (!definition.retryPolicy().enabled() || attempt >= maxAttempts) {
      return false;
    }

    if (result.retryable()) {
      return true;
    }

    return definition.retryPolicy().retryableStatusCodes().contains(result.statusCode());
  }

  private long nextDelay(final ConnectorDefinition definition, final long currentDelayMs) {
    if (!definition.retryPolicy().enabled()) {
      return 0L;
    }

    if (definition.retryPolicy().backoffTypeEnum() == RetryBackoffType.EXPONENTIAL) {
      final long nextDelay =
          Math.round(currentDelayMs * definition.retryPolicy().backoffMultiplier());
      return Math.min(nextDelay, definition.retryPolicy().maxDelayMs());
    }

    return Math.min(
        definition.retryPolicy().initialDelayMs(), definition.retryPolicy().maxDelayMs());
  }

  private static void sleep(final long delayMs) {
    if (delayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ConnectorExecutionException("Retry sleep interrupted", ex);
    }
  }

  private static List<Object> asItems(final Object mappedData) {
    if (mappedData == null) {
      return List.of();
    }
    if (mappedData instanceof Collection<?> collection) {
      return new ArrayList<>(collection);
    }
    return List.of(mappedData);
  }

  private static Map<String, Object> toPreviousMap(final ConnectorExecutionResult pageResult) {
    final Map<String, Object> previous = new HashMap<>();
    previous.put("statusCode", pageResult.statusCode());
    previous.put("success", pageResult.success());
    previous.put("data", pageResult.mappedData());
    previous.put("metadata", pageResult.metadata());
    previous.put("rawBody", pageResult.rawResponseBody());
    previous.put("errorCode", pageResult.errorCode());
    previous.put("errorMessage", pageResult.errorMessage());
    previous.put("retryable", pageResult.retryable());
    return previous;
  }

  private static DefaultResponseMapper createDefaultResponseMapper() {
    final ResponseExtractor responseExtractor = new JsonPathResponseExtractor();
    return new DefaultResponseMapper(
        responseExtractor, new DefaultErrorClassifier(responseExtractor));
  }
}
