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
  void shouldReturnClientErrorsForDuplicateAndInvalidModelCreate() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "model-duplicate-it",
              "name": "Model Duplicate IT",
              "type": "ollama",
              "model": "qwen2.5",
              "baseUrl": "http://localhost:11434"
            }
            """)
        .when()
        .post("/v1/model")
        .then()
        .statusCode(200)
        .body("id", equalTo("model-duplicate-it"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "model-duplicate-it",
              "name": "Model Duplicate IT",
              "type": "ollama",
              "model": "qwen2.5",
              "baseUrl": "http://localhost:11434"
            }
            """)
        .when()
        .post("/v1/model")
        .then()
        .statusCode(409);

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/v1/model")
        .then()
        .statusCode(400);
  }

  @Test
  void shouldReturnClientErrorsForMissingAndInvalidModelUpdate() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "model-update-it",
              "name": "Model Update IT",
              "type": "ollama",
              "model": "qwen2.5",
              "baseUrl": "http://localhost:11434"
            }
            """)
        .when()
        .post("/v1/model/upsert")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "model-update-it",
              "name": "Model Update IT 2",
              "type": "ollama",
              "model": "qwen2.5",
              "baseUrl": "http://localhost:11434"
            }
            """)
        .when()
        .put("/v1/model/model-update-it")
        .then()
        .statusCode(200)
        .body("name", equalTo("Model Update IT 2"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "missing-model-update-it",
              "name": "Missing",
              "type": "ollama",
              "model": "qwen2.5",
              "baseUrl": "http://localhost:11434"
            }
            """)
        .when()
        .put("/v1/model/missing-model-update-it")
        .then()
        .statusCode(404);

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .put("/v1/model/model-update-it")
        .then()
        .statusCode(400);
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
  void shouldReturnClientErrorsForDuplicateAndInvalidAgentCreate() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "agent-duplicate-it",
              "type": "default",
              "name": "Agent Duplicate IT",
              "modelId": "model-it"
            }
            """)
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(200)
        .body("id", equalTo("agent-duplicate-it"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "agent-duplicate-it",
              "type": "default",
              "name": "Agent Duplicate IT",
              "modelId": "model-it"
            }
            """)
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(409);

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/v1/agent/agent")
        .then()
        .statusCode(400);
  }

  @Test
  void shouldReturnNotFoundWhenUpdatingMissingAgent() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "id": "missing-agent-update-it",
              "type": "default",
              "name": "Missing Agent",
              "modelId": "model-it"
            }
            """)
        .when()
        .put("/v1/agent/agent/missing-agent-update-it")
        .then()
        .statusCode(404);
  }

  @Test
  void shouldReturnNotFoundWhenDeletingMissingAgent() {
    given()
        .when()
        .delete("/v1/agent/agent/missing-agent-delete-it")
        .then()
        .statusCode(404);
  }

  @Test
  void shouldReturnNotFoundWhenInvokingUnknownAgent() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "type": "STREAM_AGUI_EVENTS",
              "agentId": "missing-agent-events-it",
              "message": "hello"
            }
            """)
        .when()
        .post("/v1/agent/events")
        .then()
        .statusCode(404);
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
