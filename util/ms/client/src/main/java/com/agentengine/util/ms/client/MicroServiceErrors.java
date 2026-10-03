package com.agentengine.util.ms.client;

import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.google.common.base.Throwables;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * How a microservice call's failure crosses the wire: the server turns it into a gRPC status, and
 * the client turns a {@link Status.Code#PERMISSION_DENIED} back into the {@link
 * UnauthorizedException} it was, the denied asset included, so callers handle a remote denial as
 * they would a local one.
 */
public final class MicroServiceErrors {

  private static final Metadata.Key<String> ASSET_TYPE =
      Metadata.Key.of("asset-type", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> ASSET_ID =
      Metadata.Key.of("asset-id", Metadata.ASCII_STRING_MARSHALLER);

  private MicroServiceErrors() {}

  /** The status, and the trailers, the server answers a failed call with. */
  public static StatusRuntimeException toStatusException(final Throwable throwable) {
    final Throwable cause = Throwables.getRootCause(throwable);
    final Metadata trailers = new Metadata();
    final Status status =
        switch (cause) {
          case AssetNotFoundException _ -> Status.NOT_FOUND;
          case DuplicateAssetException _ -> Status.ALREADY_EXISTS;
          case UnauthorizedException unauthorized -> {
            putIfPresent(trailers, ASSET_TYPE, unauthorized.getAssetType());
            putIfPresent(trailers, ASSET_ID, unauthorized.getAssetId());
            yield Status.PERMISSION_DENIED;
          }
          case IllegalArgumentException _, ConfigurationException _ -> Status.INVALID_ARGUMENT;
          default -> Status.INTERNAL;
        };
    return status.withDescription(cause.getMessage()).withCause(cause).asRuntimeException(trailers);
  }

  /** The exception a failed call raises at the client. */
  public static Throwable fromStatusException(final Throwable throwable) {
    if (!(throwable instanceof StatusRuntimeException exception)
        || exception.getStatus().getCode() != Status.Code.PERMISSION_DENIED) {
      return throwable;
    }
    final Metadata trailers = exception.getTrailers();
    final String assetType = trailers == null ? null : trailers.get(ASSET_TYPE);
    return assetType == null
        ? new UnauthorizedException(exception.getStatus().getDescription())
        : new UnauthorizedException(assetType, trailers.get(ASSET_ID));
  }

  private static void putIfPresent(
      final Metadata trailers, final Metadata.Key<String> key, final String value) {
    if (value != null) {
      trailers.put(key, value);
    }
  }
}
