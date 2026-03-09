package com.agentengine.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.agentengine.engine.repository.MongoClientFactory;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(com.agentengine.interfaces.rest.testing.MongoRedisTestResource.class)
class ModelAndAgentEndpointsIT {

  @Inject MongoClientFactory mongoClientFactory;

  @BeforeEach
  void shouldResetDatabaseWhenTestStarts() {
    try (MongoClient client = mongoClientFactory.getClient()) {
      client.getDatabase("AGENT_ENGINE").drop();
    }
  }

  @Test
  void shouldCreateAndGetModelWhenModelEndpointsInvoked() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "model-it",
              "name": "Model IT",
              "type": "ollama",
              "model": "qwen2.5"
            }
            """)
        .when()
        .post("/v1/model/upsert")
        .then()
        .statusCode(200)
        .body("id", equalTo("model-it"))
        .body("type", equalTo("ollama"));

    given()
        .when()
        .get("/v1/model/model-it")
        .then()
        .statusCode(200)
        .body("id", equalTo("model-it"))
        .body("name", equalTo("Model IT"));
  }

  @Test
  void shouldCreateUpdateAndDeleteAgentWhenAgentEndpointsInvoked() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "agent-it",
              "type": "default",
              "name": "Agent IT",
              "modelId": "model-it"
            }
            """)
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(200)
        .body("id", equalTo("agent-it"))
        .body("name", equalTo("Agent IT"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "agent-it",
              "type": "default",
              "name": "Agent IT Updated",
              "modelId": "model-it"
            }
            """)
        .when()
        .put("/v1/agent/agent/agent-it")
        .then()
        .statusCode(200)
        .body("name", equalTo("Agent IT Updated"));

    given()
        .when()
        .delete("/v1/agent/agent/agent-it")
        .then()
        .statusCode(200)
        .body(equalTo("true"));
  }

  @Test
  void shouldReturnNotFoundWhenResumingUnknownSession() {
    given()
        .contentType("application/json")
        .body("{\"message\":\"resume\"}")
        .when()
        .post("/v1/agent/session/missing-session/resume/events")
        .then()
        .statusCode(404);
  }
}
