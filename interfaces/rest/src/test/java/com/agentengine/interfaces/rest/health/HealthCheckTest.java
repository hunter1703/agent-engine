package com.agentengine.interfaces.rest.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class HealthCheckTest {

  @Test
  public void testLivenessProbe() {
    given().when().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
  }

  @Test
  public void testReadinessProbe() {
    given().when().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
  }
}
