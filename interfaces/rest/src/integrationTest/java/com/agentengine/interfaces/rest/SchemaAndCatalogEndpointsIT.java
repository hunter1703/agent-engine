package com.agentengine.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(com.agentengine.interfaces.rest.testing.MongoRedisTestResource.class)
class SchemaAndCatalogEndpointsIT {

  @Inject
  MongoClientFactory mongoClientFactory;

  @BeforeEach
  void shouldResetDatabaseWhenTestStarts() {
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
          "type": "default",
          "name": "Agent Catalog",
          "modelId": "model-catalog"
        }
        """).when().post("/v1/agent/agent").then().statusCode(200);
  }

  @Test
  void shouldReturnSchemaWhenSchemaEndpointCalledForKnownAssetType() {
    given().when().get("/schemas/model").then().statusCode(200).body("size()", greaterThanOrEqualTo(1));
  }

  @Test
  void shouldReturnBadRequestWhenSchemaLookupUnsupportedAssetType() {
    given().contentType("application/json").body("{\"assetType\":\"unsupported\",\"assetId\":\"x\",\"agentId\":\"a\"}").when()
        .post("/schemas/").then().statusCode(400);
  }

  @Test
  void shouldListModelsWhenCatalogListEndpointCalled() {
    given().contentType("application/json").body("{\"assetType\":\"model\"}").when().post("/v1/catalog/list").then().statusCode(200)
        .body("items.size()", greaterThanOrEqualTo(1));
  }

  @Test
  void shouldReturnBadRequestWhenCatalogListUnsupportedType() {
    given().contentType("application/json").body("{\"assetType\":\"unsupported\"}").when().post("/v1/catalog/list").then().statusCode(400);
  }
}
