package com.agentengine.util.mongodb.mongo;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.EncryptionService;
import com.agentengine.util.mongodb.config.ApplicationConfig;
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
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MongoClientFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MongoClientFactory.class);
    private static final String INFRA_MONGO_URI_KEY = "infra.mongodb.uri";

    private final MongoClientSupport mongoClientSupport;
    private final Instance<EncryptionService> encryptionService;
    private final ApplicationConfig applicationConfig;

    @Inject
    public MongoClientFactory(
            MongoClientSupport mongoClientSupport,
            Instance<EncryptionService> encryptionService,
            ApplicationConfig applicationConfig) {
        this.mongoClientSupport = mongoClientSupport;
        this.encryptionService = encryptionService;
        this.applicationConfig = applicationConfig;
    }

    public MongoClient getClient() {
        return MongoClients.create(buildClientSettings(
                applicationConfig.getString(INFRA_MONGO_URI_KEY),
                getBsonDiscriminators(mongoClientSupport),
                encryptionService));
    }

    private static List<String> getBsonDiscriminators(final MongoClientSupport mongoClientSupport) {
        return CollectionUtils.nullSafeList(mongoClientSupport.getBsonDiscriminators());
    }

    private MongoClientSettings buildClientSettings(
            final String connectionStringStr,
            final List<String> bsonDiscriminators,
            final Instance<EncryptionService> encryptionService) {
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
                pojoCodecProviderBuilder.register(ClassModel.builder(Class.forName(discriminator, true, classLoader))
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
        final CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(), fromProviders(pojoCodecProviderBuilder.build()));
        return MongoClientSettings.builder()
                .applicationName("agent-engine")
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .build();
    }
}
