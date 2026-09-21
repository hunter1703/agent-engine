package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.infra.ServerType;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.EnvUtils;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.infra.InfraClientFactory;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.mongodb.infra.MongoClientInfraConfig;
import com.agentengine.util.mongodb.infra.MongoServerInfraConfig;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
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

// ApplicationScoped, not Singleton: the infra config service is built on this factory, and this
// factory reads the client configs through the infra config service.
@ApplicationScoped
public class MongoClientFactory
    extends InfraClientFactory<MongoClientInfraConfig, MongoServerInfraConfig, MongoClient> {
  private static final Logger LOG = LoggerFactory.getLogger(MongoClientFactory.class);
  private final MongoClientSupport mongoClientSupport;
  private final EncryptionService encryptionService;
  private final String defaultServerId;
  private final Instance<Codec<?>> customCodecs;
  private final LazyLoader<MongoClient> infraClient;

  @Inject
  public MongoClientFactory(
          InfraConfigService infraConfigService,
          DistributedCacheManager cacheManager,
          MongoClientSupport mongoClientSupport,
          EncryptionService encryptionService, ApplicationConfig applicationConfig,
          Instance<Codec<?>> customCodecs) {
    super(
        infraConfigService, cacheManager, ServerType.MONGO_SERVER);
    this.mongoClientSupport = mongoClientSupport;
    this.encryptionService = encryptionService;
      this.defaultServerId = ServerType.MONGO_SERVER.defaultServerId(applicationConfig);
      this.customCodecs = customCodecs;
    this.infraClient = new LazyLoader<>(() -> create(EnvUtils.getInfraMongoUri()));
  }

  public MongoClient getInfraClient() {
    return infraClient.get();
  }

  public MongoClient getClient(final MongoStoreClientType clientType, final Integer customerId) {
    return get(
        getOrCreate(
            MongoUtils.clientId(clientType.name(), customerId),
            () ->
                MongoUtils.clientConfig(
                    clientType.name(), customerId, defaultServerId)));
  }

  @Override
  protected MongoClient create(final MongoServerInfraConfig serverConfig) {
    return create(serverConfig.getUri());
  }

  private MongoClient create(final String uri) {
    return MongoClients.create(
        buildClientSettings(
            uri, getBsonDiscriminators(mongoClientSupport), encryptionService, customCodecs));
  }

  private static List<String> getBsonDiscriminators(final MongoClientSupport mongoClientSupport) {
    return CollectionUtils.nullSafeList(mongoClientSupport.getBsonDiscriminators());
  }

  private static MongoClientSettings buildClientSettings(
      final String connectionStringStr,
      final List<String> bsonDiscriminators,
      final EncryptionService encryptionService,
      final Instance<Codec<?>> customCodecs) {
    final ConnectionString connectionString = new ConnectionString(connectionStringStr);

    final List<Convention> conventions = new ArrayList<>(Conventions.DEFAULT_CONVENTIONS);
    // SecurePropertyConvention must be added last: it relies on the standard
    // conventions having already built the property model list before it can
    // annotate secure fields.
    conventions.add(new SecurePropertyConvention(encryptionService));

    PojoCodecProvider.Builder pojoCodecProviderBuilder =
        PojoCodecProvider.builder().conventions(conventions).automatic(true);
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    for (final String discriminator : CollectionUtils.nullSafeList(bsonDiscriminators)) {
      try {
        // enableDiscriminator(true) primes the ClassModel's discriminator before
        // conventions run, ensuring the discriminator registry is populated and
        // the DiscriminatorLookup can find concrete subtypes at decode time.
        // @BsonDiscriminator annotations (processed by ANNOTATION_CONVENTION) then
        // override the key/value as specified on each class.
        pojoCodecProviderBuilder.register(
            ClassModel.builder(Class.forName(discriminator, true, classLoader))
                .enableDiscriminator(true)
                .conventions(conventions)
                .build());
      } catch (ClassNotFoundException ex) {
        LOG.warn(
            "Discriminator class '{}' not found — codec registration skipped; "
                + "@Secure fields may be stored unencrypted",
            discriminator);
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
