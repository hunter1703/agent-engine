package com.agentengine.interfaces.rest;

import com.agentengine.engine.api.AgentRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AgentValidationTest {

  @Test
  void testInvalidAgentRequest() {
    AgentRequest request = new AgentRequest();
    // Missing agentId, sessionId, message

    given().contentType(ContentType.JSON).body(request).when().post("/v1/agent/invoke").then().log().all()
        .statusCode(400).body("title", is("Constraint Violation")).body("status", is(400));
  }

  @Test
  void testValidAgentRequest() {
    AgentRequest request = new AgentRequest();
    request.setAgentId("agent-1");
    request.setSessionId("session-1");
    request.setMessage("hello");
    request.setType("INVOKE_AGENT");

    // We expect 500 or 404 because the agent/session might not exist,
    // but NOT 400 Bad Request from validation.
    // Since we are mocking nothing here, it will likely fail deeper in the stack.
    // But for validation check, passing validation is enough.

    // Actually, without mocks, it might fail with 500.
    // We just want to ensure it's NOT 400 due to validation.
    // However, if the service logic throws WebApplicationException, it might be
    // mapped.
    // Let's just check valid request doesn't return constraint violation.

    given().contentType(ContentType.JSON).body(request).when().post("/v1/agent/invoke").then()
        .statusCode(org.hamcrest.Matchers.not(400));
  }

  @Test
  void testRequestLoggingFilter() {
    AgentRequest request = new AgentRequest();
    // invalid request is fine, filter should still run
    given().contentType(ContentType.JSON).body(request).when().post("/v1/agent/invoke").then().statusCode(400)
        .header("X-Request-ID", notNullValue());
  }
}
