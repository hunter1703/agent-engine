package com.agentengine.interfaces.rest.testing;

import com.redis.testcontainers.RedisContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoRedisTestResource implements QuarkusTestResourceLifecycleManager {

  private static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"));

  private static final RedisContainer REDIS_CONTAINER = new RedisContainer(DockerImageName.parse("redis:7.2-alpine"));

  @Override
  public Map<String, String> start() {
    if (!MONGO_CONTAINER.isRunning()) {
      MONGO_CONTAINER.start();
    }
    if (!REDIS_CONTAINER.isRunning()) {
      REDIS_CONTAINER.start();
    }

    final String redisUri = "redis://" + REDIS_CONTAINER.getHost() + ":" + REDIS_CONTAINER.getFirstMappedPort();

    return Map.of("quarkus.mongodb.devservices.enabled", "false", "quarkus.mongodb.connection-string", MONGO_CONTAINER.getReplicaSetUrl(),
        "quarkus.http.test-port", "0", "quarkus.grpc.server.port", "0", "test.redis.uri", redisUri);
  }

  @Override
  public void stop() {
    if (REDIS_CONTAINER.isRunning()) {
      REDIS_CONTAINER.stop();
    }
    if (MONGO_CONTAINER.isRunning()) {
      MONGO_CONTAINER.stop();
    }
  }
}
