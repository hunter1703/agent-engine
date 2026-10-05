package com.agentengine.util.mongodb.mongo;

import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.crypto.EncryptionService;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MongoClientBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(MongoClientBuilder.class);

  private final List<String> bsonDiscriminators;
  private final Instance<Codec<?>> customCodecs;

  @Inject
  public MongoClientBuilder(
      MongoClientSupport mongoClientSupport, Instance<Codec<?>> customCodecs) {
    this.bsonDiscriminators =
        CollectionUtils.nullSafeList(mongoClientSupport.getBsonDiscriminators());
    this.customCodecs = customCodecs;
  }

  public MongoClient get(final String uri, final EncryptionService encryptionService) {
    return MongoClients.create(buildClientSettings(uri, encryptionService));
  }

  private MongoClientSettings buildClientSettings(
      final String connectionStringStr, final EncryptionService encryptionService) {
    final ConnectionString connectionString = new ConnectionString(connectionStringStr);

    final List<Convention> conventions = new ArrayList<>(Conventions.DEFAULT_CONVENTIONS);
    if (encryptionService != null) {
      conventions.add(new SecurePropertyConvention(encryptionService));
    }

    PojoCodecProvider.Builder pojoCodecProviderBuilder =
        PojoCodecProvider.builder().conventions(conventions).automatic(true);
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    for (final String discriminator : bsonDiscriminators) {
      try {
        pojoCodecProviderBuilder.register(
            ClassModel.builder(Class.forName(discriminator, true, classLoader))
                .enableDiscriminator(true)
                .conventions(conventions)
                .build());
      } catch (ClassNotFoundException ex) {
        LOG.warn("Discriminator class '{}' not found", discriminator);
      }
    }
    final CodecRegistry codecRegistry =
        fromRegistries(
            fromCodecs(customCodecs.stream().toList()),
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(pojoCodecProviderBuilder.build()));
    return MongoClientSettings.builder()
        .applicationName("agent-engine")
        .applyConnectionString(connectionString)
        .codecRegistry(codecRegistry)
        .build();
  }
}
