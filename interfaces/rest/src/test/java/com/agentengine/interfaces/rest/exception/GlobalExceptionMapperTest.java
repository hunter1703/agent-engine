package com.agentengine.interfaces.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.interfaces.rest.dto.ErrorResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class GlobalExceptionMapperTest {

  @Test
  public void testMapRuntimeException() {
    GlobalExceptionMapper mapper = new GlobalExceptionMapper();
    RuntimeException ex = new RuntimeException("Something went wrong");

    Response response = mapper.toResponse(ex);

    Assertions.assertEquals(500, response.getStatus());
    ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("500");
    assertThat(error.message()).isEqualTo("Internal Server Error");
    assertThat(error.traceId()).isNotNull();
  }

  @Test
  public void testMapWebApplicationException() {
    GlobalExceptionMapper mapper = new GlobalExceptionMapper();
    WebApplicationException ex = new WebApplicationException("Not Found", 404);

    Response response = mapper.toResponse(ex);

    Assertions.assertEquals(404, response.getStatus());
    ErrorResponse error = (ErrorResponse) response.getEntity();
    assertThat(error.code()).isEqualTo("404");
    assertThat(error.message()).isEqualTo("Not Found");
    assertThat(error.traceId()).isNotNull();
  }
}
