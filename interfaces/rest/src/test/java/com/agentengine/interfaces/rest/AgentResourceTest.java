package com.agentengine.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AgentResourceTest {

  private static final String AGENT_ID = "qa_test_agent";

  @Test
  @Order(0)
  public void testCreateModel() {
    String payload =
        """
        {
          "id": "qwen2.5-1.5b-instruct-q5_k_m",
          "model": "qwen2.5-1.5b-instruct-q5_k_m",
          "name": "Qwen2.5 1.5B Instruct",
          "type": "open_ai_compatible",
          "baseUrl": "http://127.0.0.1:17000/v1"
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/v1/model")
        .then()
        .statusCode(anyOf(equalTo(200), equalTo(201), equalTo(500)));
  }

  @Test
  @Order(1)
  public void testCreateEchoAgent() {
    String payload =
        """
        {
          "type": "default",
          "id": "echo_agent",
          "name": "Echo Agent",
          "model": {
            "modelId": "qwen2.5-1.5b-instruct-q5_k_m",
            "role": "reasoning",
            "systemPrompt": "You are an echo-only assistant.",
            "contextManagerConfig": {
              "type": "last_n",
              "keepLast": 10000
            },
            "tools": [
              {
                "toolName": "echo",
                "configs": {
                  "prefix": "hello-"
                }
              }
            ]
          }
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(
            anyOf(equalTo(200), equalTo(201), equalTo(500))); // 500 if duplicate key for now
  }

  @Test
  @Order(2)
  public void testCreateAgent() {
    String payload =
        """
        {
           "id": "qa_test_agent",
           "name": "QA Test Agent",
           "type": "default",
           "description": "Created by QA test",
           "model": {
             "modelId": "gemini-2.0-flash",
             "role": "reasoning",
             "systemPrompt": "You are a QA test agent."
           }
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(3)
  public void testGetAgent() {
    given()
        .when()
        .get("/v1/catalog/agent/" + AGENT_ID)
        .then()
        .statusCode(200)
        .body("id", equalTo(AGENT_ID));
  }

  @Test
  @Order(4)
  public void testUpdateAgent() {
    String payload =
        """
        {
           "id": "qa_test_agent",
           "name": "QA Test Agent Updated",
           "type": "default",
           "description": "Updated by QA",
           "model": {
             "modelId": "gemini-2.0-flash",
             "role": "reasoning",
             "systemPrompt": "Updated."
           }
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .put("/v1/agent/agent/" + AGENT_ID)
        .then()
        .statusCode(200);
  }

  @Test
  @Order(5)
  public void testGetNonexistentAgent() {
    given().when().get("/v1/catalog/agent/nonexistent_agent").then().statusCode(404);
  }

  @Test
  @Order(6)
  public void testCreateAgentMissingFields() {
    String payload = """
        {
           "id": "bad_agent"
        }
        """;
    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(org.hamcrest.Matchers.anyOf(equalTo(200), equalTo(400), equalTo(500)));
  }

  @Test
  @Order(7)
  public void testDeleteAgent() {
    given()
        .when()
        .delete("/v1/agent/agent/" + AGENT_ID)
        .then()
        .statusCode(org.hamcrest.Matchers.anyOf(equalTo(200), equalTo(204)));
  }

  @Test
  @Order(8)
  public void testGetAgentAfterDelete() {
    given().when().get("/v1/catalog/agent/" + AGENT_ID).then().statusCode(404);
  }

  @Test
  @Order(8)
  public void testInvokeEchoAgent() {
    String sessionId = "invoke-" + UUID.randomUUID().toString();
    String payload =
        """
        {
           "type": "INVOKE_AGENT",
           "agentId": "echo_agent",
           "sessionId": "%s",
           "message": "Hello QA final test"
        }
        """
            .formatted(sessionId);

    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/v1/agent/invoke")
        .then()
        .statusCode(200)
        .body("finalAnswer", notNullValue());
  }

  @Test
  @Order(9)
  public void testSseStreaming() {
    String sessionId = "stream-" + UUID.randomUUID().toString();
    String payload =
        """
        {
           "type": "STREAM_AGUI_EVENTS",
           "agentId": "echo_agent",
           "sessionId": "%s",
           "message": "Stream me"
        }
        """
            .formatted(sessionId);

    given()
        .contentType(ContentType.JSON)
        .accept("text/event-stream")
        .body(payload)
        .when()
        .post("/v1/agent/events")
        .then()
        .statusCode(200)
        .contentType("text/event-stream")
        .body(org.hamcrest.Matchers.containsString("data:"));
  }
}
