package com.agentengine.cli.beans;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.cli.beans.Request.RequestType;
import org.junit.jupiter.api.Test;

class RequestTest {

  @Test
  void requestStoresTypeAndUserMessage() {
    Request request = new Request();
    request.setType(RequestType.INVOKE_AGENT);
    request.setId("req-1");
    request.setUserMessage("hello");

    assertThat(request.getType()).isEqualTo(RequestType.INVOKE_AGENT);
    assertThat(request.getId()).isEqualTo("req-1");
    assertThat(request.getUserMessage()).isEqualTo("hello");
  }
}
