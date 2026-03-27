package com.agentengine.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.interfaces.rest.testing.MongoRedisTestResource;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(MongoRedisTestResource.class)
public class SchemaAndCatalogEndpointsIT {

  @Inject
  private MongoClientFactory mongoClientFactory;
  @Inject
  private AgentSessionRepository agentSessionRepository;

  @BeforeEach
  public void shouldResetDatabaseWhenTestStarts() {
    try (MongoClient client = mongoClientFactory.getClient()) {
      client.getDatabase("AGENT_ENGINE").drop();
    }

    given().contentType("application/json").body("""
        {
          "id": "model-catalog",
          "name": "Model Catalog",
          "type": "ollama",
          "model": "qwen2.5"
        }
        """).when().post("/v1/model/upsert").then().statusCode(200);

    given().contentType("application/json").body("""
        {
          "id": "agent-catalog",
          "type": "DEFAULT",
          "name": "Agent Catalog",
          "systemPrompt": "Catalog agent prompt",
          "modelId": "model-catalog"
        }
        """).when().post("/v1/agent/upsert").then().statusCode(200);
  }

  @Test
  public void shouldReturnSchemaWhenSchemaEndpointCalledForKnownAssetType() {
    given().when().get("/schemas/model").then().statusCode(200).body("size()", greaterThanOrEqualTo(1));
  }

  @Test
  public void shouldReturnBadRequestWhenSchemaLookupUnsupportedAssetType() {
    given().contentType("application/json").body("{\"assetType\":\"unsupported\",\"assetId\":\"x\",\"agentId\":\"a\"}").when()
        .post("/schemas/").then().statusCode(400);
  }

  @Test
  public void shouldListModelsWhenCatalogListEndpointCalled() {
    given().contentType("application/json").body("{\"assetType\":\"model\"}").when().post("/v1/catalog/list").then().statusCode(200)
        .body("items.size()", greaterThanOrEqualTo(1));
  }

  @Test
  public void shouldListSessionsWhenCatalogListEndpointCalledWithoutPage() {
    agentSessionRepository.createSession("agent-catalog", "default", new ConcurrentHashMap<>(), "session-catalog-1").blockingGet();

    given().contentType("application/json").body("{\"assetType\":\"session\"}").when().post("/v1/catalog/list").then().statusCode(200)
        .body("items.size()", greaterThanOrEqualTo(1));
  }

  @Test
  public void shouldReturnAgentWhenCatalogSearchFiltersById() {
    given().contentType("application/json").body("""
        {
          "assetType": "agent",
          "query": {
            "filter": {
              "field": "id",
              "op": "EQ",
              "values": ["agent-catalog"]
            },
            "includeCount": true
          }
        }
        """).when().post("/v1/catalog/search").then().statusCode(200).body("items.size()", greaterThanOrEqualTo(1))
        .body("items[0].id", Matchers.equalTo("agent-catalog")).body("total", Matchers.greaterThanOrEqualTo(1));
  }

  @Test
  public void shouldReturnBadRequestWhenCatalogListUnsupportedType() {
    given().contentType("application/json").body("{\"assetType\":\"unsupported\"}").when().post("/v1/catalog/list").then().statusCode(400);
  }
}
