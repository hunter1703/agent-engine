package com.agentengine.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SchemaResourceTest {

  @Test
  public void testGetSchema() {
    given()
        .when()
        .get("/schemas/agent")
        .then()
        .statusCode(200)
        .body("schema.title", equalTo("Agent Configuration"));
  }

  @Test
  public void testResolveToolConfigSchema() {
    String payload =
        """
            {
                "assetType": "tool_configs",
                "assetId": "echo",
                "agentId": "ALL"
            }
            """;

    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/schemas")
        .then()
        .statusCode(200)
        .body("type", equalTo("object"))
        .body("properties.prefix", notNullValue());
  }

  @Test
  public void testUnsupportedPostType() {
    String payload =
        """
            {
                "assetType": "unsupported",
                "assetId": "any",
                "agentId": "ALL"
            }
            """;
    given()
        .contentType(ContentType.JSON)
        .body(payload)
        .when()
        .post("/schemas")
        .then()
        .statusCode(400);
  }
}
