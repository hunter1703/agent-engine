package com.agentengine.util.ms.client;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.exception.*;
import com.agentengine.util.infra.ClientType;
import com.google.common.base.Throwables;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

public final class MicroServiceUtils {
  private static final Logger log = LoggerFactory.getLogger(MicroServiceUtils.class);

  private static final Metadata.Key<String> ERROR_CLASS_KEY =
      Metadata.Key.of("error-class", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> ERROR_PAYLOAD_KEY =
      Metadata.Key.of("error-payload", Metadata.ASCII_STRING_MARSHALLER);

  private MicroServiceUtils() {}

  public static String defaultServerId(final String service) {
    return service + "-default";
  }

  public static String clientId(final String customerId, final String service) {
    return ClientType.MICROSERVICE_CLIENT + ID_SEPARATOR + service + ID_SEPARATOR + customerId;
  }

  public static MicroServiceClientInfraConfig clientConfig(
      final String customerId, final String service, final String serverId) {
    final MicroServiceClientInfraConfig clientConfig = new MicroServiceClientInfraConfig();
    clientConfig.setCustomerId(customerId);
    clientConfig.setService(service);
    clientConfig.setServerId(serverId);
    return clientConfig;
  }

  /** The status, and the trailers, the server answers a failed call with. */
  public static StatusRuntimeException toStatusException(final Throwable throwable) {
    final Throwable cause = Throwables.getRootCause(throwable);
    final Metadata trailers = new Metadata();
    final Status status =
        switch (cause) {
          case AssetNotFoundException _ -> Status.NOT_FOUND;
          case DuplicateAssetException _ -> Status.ALREADY_EXISTS;
          case StaleStateException _ -> Status.ABORTED;
          case UnauthorizedException _ -> Status.PERMISSION_DENIED;
          case ConfigurationException _ -> Status.FAILED_PRECONDITION;
          case IllegalArgumentException _ -> Status.INVALID_ARGUMENT;
          case UnknownHostException _, ConnectException _, NoRouteToHostException _ ->
              Status.UNAVAILABLE;
          case StatusRuntimeException exception
              when exception.getStatus().getCode() == Status.Code.UNAVAILABLE ->
              Status.UNAVAILABLE;
          default -> Status.INTERNAL;
        };

    if (cause instanceof ApplicationException) {
      trailers.put(ERROR_CLASS_KEY, cause.getClass().getName());
      trailers.put(ERROR_PAYLOAD_KEY, JsonUtils.toJson(cause));
    }

    return status.withDescription(cause.getMessage()).withCause(cause).asRuntimeException(trailers);
  }

  /** The exception a failed call raises at the client. */
  public static Throwable fromStatusException(final Throwable throwable) {
    if (!(throwable instanceof StatusRuntimeException exception)) {
      return throwable;
    }
    final Metadata trailers =
        exception.getTrailers() == null ? new Metadata() : exception.getTrailers();

    final String errorClassName = trailers.get(ERROR_CLASS_KEY);
    if (errorClassName != null) {
      try {
        final Class<?> exceptionClass = Class.forName(errorClassName);
        if (ApplicationException.class.isAssignableFrom(exceptionClass)) {
          final String jsonPayload = trailers.get(ERROR_PAYLOAD_KEY);
          if (jsonPayload != null) {
            return (Throwable) JsonUtils.fromJson(jsonPayload, exceptionClass);
          }
        } else {
          log.warn("Untrusted exception class received via gRPC trailers: {}", errorClassName);
        }
      } catch (ClassNotFoundException e) {
        log.warn("Unknown exception class received via gRPC trailers: {}", errorClassName);
      }
    }

    final String description = exception.getStatus().getDescription();
    return switch (exception.getStatus().getCode()) {
      case NOT_FOUND ->
          new AssetNotFoundException("Unknown", description != null ? description : "Not found");
      case ALREADY_EXISTS ->
          new DuplicateAssetException(
              "Unknown", description != null ? description : "Already exists");
      case ABORTED -> new StaleStateException(description != null ? description : "Unknown", 0L);
      case PERMISSION_DENIED -> new UnauthorizedException(description);
      case FAILED_PRECONDITION -> new ConfigurationException(description);
      case INVALID_ARGUMENT -> new IllegalArgumentException(description);
      default -> exception;
    };
  }
}
