package com.agentengine.interfaces.rest.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HealthResourceTest {

  @Test
  public void testHealthEndpoint() {
    given().when().get("/q/health").then().statusCode(200).body("status", is("UP"));
  }

  @Test
  public void testLivenessEndpoint() {
    given().when().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
  }

  @Test
  public void testReadinessEndpoint() {
    given().when().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
  }
}
