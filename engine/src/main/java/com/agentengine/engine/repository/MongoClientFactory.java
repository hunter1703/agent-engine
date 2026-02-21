package com.agentengine.engine.repository;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import java.util.List;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.microprofile.config.ConfigProvider;

public final class MongoClientFactory {
  private static final String DEFAULT_CONNECTION = "mongodb://localhost:27017";

  private MongoClientFactory() {}

  public static MongoClient createClient(
      final MongoClientSupport mongoClientSupport, String connectionString) {
    connectionString =
        StringUtils.isNotBlank(connectionString) ? connectionString : resolveConnectionString();
    return MongoClients.create(
        buildClientSettings(connectionString, getBsonDiscriminators(mongoClientSupport)));
  }

  private static String resolveConnectionString() {
    return ConfigProvider.getConfig()
        .getOptionalValue("quarkus.mongodb.connection-string", String.class)
        .orElseGet(
            () -> {
              final String fromEnv = System.getenv("MONGODB_CONNECTION_STRING");
              return StringUtils.isNotBlank(fromEnv) ? fromEnv : DEFAULT_CONNECTION;
            });
  }

  private static List<String> getBsonDiscriminators(final MongoClientSupport mongoClientSupport) {
    return CollectionUtils.nullSafeList(mongoClientSupport.getBsonDiscriminators());
  }

  private static MongoClientSettings buildClientSettings(
      final String connectionStringStr, final List<String> bsonDiscriminators) {
    final ConnectionString connectionString = new ConnectionString(connectionStringStr);
    PojoCodecProvider.Builder pojoCodecProviderBuilder =
        PojoCodecProvider.builder().automatic(true);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    for (String discriminator : CollectionUtils.nullSafeList(bsonDiscriminators)) {
      try {
        pojoCodecProviderBuilder.register(
            ClassModel.builder(Class.forName(discriminator, true, classLoader))
                .enableDiscriminator(true)
                .build());
      } catch (ClassNotFoundException ex) {
        // Ignore
      }
    }
    CodecRegistry codecRegistry =
        fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(pojoCodecProviderBuilder.build()));
    return MongoClientSettings.builder()
        .applicationName("agent-engine")
        .applyConnectionString(connectionString)
        .codecRegistry(codecRegistry)
        .build();
  }
}
