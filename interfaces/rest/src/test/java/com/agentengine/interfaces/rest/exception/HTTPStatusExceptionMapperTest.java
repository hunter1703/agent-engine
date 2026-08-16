package com.agentengine.interfaces.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.interfaces.rest.exception.HTTPStatusExceptionMapper.ErrorResponse;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class HTTPStatusExceptionMapperTest {

  @Test
  void shouldMapGrpcInvalidArgumentToHttp400() {
    final HTTPStatusExceptionMapper mapper = new HTTPStatusExceptionMapper();
    final StatusRuntimeException exception =
        Status.INVALID_ARGUMENT.withDescription("bad request payload").asRuntimeException();

    final Response response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    final ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("400");
    assertThat(error.message()).isEqualTo("bad request payload");
  }

  @Test
  void shouldMapGrpcUnavailableToHttp503() {
    final HTTPStatusExceptionMapper mapper = new HTTPStatusExceptionMapper();
    final StatusRuntimeException exception =
        Status.UNAVAILABLE.withDescription("engine unavailable").asRuntimeException();

    final Response response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    final ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("503");
    assertThat(error.message()).isEqualTo("engine unavailable");
  }

  @Test
  void shouldMapConfigurationExceptionToHttp400() {
    final HTTPStatusExceptionMapper mapper = new HTTPStatusExceptionMapper();

    final Response response = mapper.toResponse(new ConfigurationException("bad config"));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    final ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("400");
    assertThat(error.message()).isEqualTo("bad config");
  }

  @Test
  void shouldMapIllegalArgumentExceptionToHttp400() {
    final HTTPStatusExceptionMapper mapper = new HTTPStatusExceptionMapper();

    final Response response = mapper.toResponse(new IllegalArgumentException("invalid payload"));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    final ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("400");
    assertThat(error.message()).isEqualTo("invalid payload");
  }

  @Test
  void shouldMapAssetNotFoundExceptionToHttp404() {
    final HTTPStatusExceptionMapper mapper = new HTTPStatusExceptionMapper();

    final AssetNotFoundException exception = new AssetNotFoundException("agent", "agent-123");
    final Response response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
    final ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("404");
    assertThat(error.message()).isEqualTo("Asset not found: type=agent, id=agent-123");
    assertThat(exception.getAssetType()).isEqualTo("agent");
    assertThat(exception.getAssetId()).isEqualTo("agent-123");
  }
}
