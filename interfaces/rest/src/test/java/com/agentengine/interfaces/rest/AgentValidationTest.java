package com.agentengine.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import com.agentengine.engine.api.AgentRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgentValidationTest {

  @Test
  void testInvalidAgentRequest() {
    AgentRequest request = new AgentRequest();
    // Missing agentId, sessionId, message

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/v1/agent/invoke")
        .then()
        .log()
        .all()
        .statusCode(400)
        .body("title", is("Constraint Violation"))
        .body("status", is(400));
  }

  @Test
  void testValidAgentRequest() {
    AgentRequest request = new AgentRequest();
    request.setAgentId("agent-1");
    request.setSessionId("session-1");
    request.setMessage("hello");
    request.setType("INVOKE_AGENT");

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/v1/agent/invoke")
        .then()
        .statusCode(not(400));
  }

  @Test
  void testRequestLoggingFilter() {
    AgentRequest request = new AgentRequest();
    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/v1/agent/invoke")
        .then()
        .statusCode(400)
        .header("X-Request-ID", notNullValue());
  }
}
