package com.agentengine.interfaces.rest.catalog;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class ResourceCatalogIntegrationTest {

  @Test
  public void testListAgents() {
    given().contentType(ContentType.JSON).body("{\"assetType\":\"agent\"}").when().post("/v1/catalog/list").then()
        .statusCode(200);
  }

  @Test
  public void testListModels() {
    given().contentType(ContentType.JSON).body("{\"assetType\":\"model\"}").when().post("/v1/catalog/list").then()
        .statusCode(200);
  }

  @Test
  public void testListSessions() {
    given().contentType(ContentType.JSON).body("{\"assetType\":\"session\"}").when().post("/v1/catalog/list").then()
        .statusCode(200);
  }

  @Test
  public void testListNoType() {
    given().contentType(ContentType.JSON).body("{}").when().post("/v1/catalog/list").then().statusCode(400);
  }

  @Test
  public void testListEmptyBody() {
    given().contentType(ContentType.JSON).body("").when().post("/v1/catalog/list").then().statusCode(400);
  }

  @Test
  public void testListInvalidType() {
    given().contentType(ContentType.JSON).body("{\"assetType\":\"unicorn\"}").when().post("/v1/catalog/list").then()
        .statusCode(400);
  }

  @Test
  public void testSearchAgents() {
    given().contentType(ContentType.JSON).body("{\"assetType\":\"agent\"}").when().post("/v1/catalog/search").then()
        .statusCode(200);
  }

  @Test
  public void testSearchNoType() {
    given().contentType(ContentType.JSON).body("{}").when().post("/v1/catalog/search").then().statusCode(400);
  }

  @Test
  public void testGetAgentEchoAgent() {
    given().when().get("/v1/catalog/agent/echo_agent").then().statusCode(200);
  }

  @Test
  public void testGetAgentNonexistent() {
    given().when().get("/v1/catalog/agent/nonexistent_agent").then().statusCode(404);
  }

  @Test
  public void testGetUnicornTest() {
    given().when().get("/v1/catalog/unicorn/test").then().statusCode(400);
  }

  @Test
  public void testGetSessionNonexistent() {
    given().when().get("/v1/catalog/session/nonexistent_session").then().statusCode(404);
  }
}
