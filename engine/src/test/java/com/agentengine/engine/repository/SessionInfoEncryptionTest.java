package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.engine.repository.codec.SecurePropertyConvention;
import com.agentengine.engine.utils.EncryptionService;
import com.mongodb.MongoClientSettings;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code SessionInfo.eventsJson} is stored encrypted when the full codec registry
 * (mirroring what {@code MongoClientFactory} builds) is used — including the discriminator-class
 * registration path that previously bypassed {@code SecurePropertyConvention}.
 */
class SessionInfoEncryptionTest {

  @Test
  @SuppressWarnings("unchecked")
  void appendEventPathEncryptsEventsJson() {
    // Arrange: mock encryption the same way SecurePropertyConventionTest does.
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    final EncryptionService encryptionService = mock(EncryptionService.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);
    when(encryptionInstance.get()).thenReturn(encryptionService);
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(encryptionService.encrypt(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(inv -> "encrypted(" + inv.getArgument(0) + ")");

    // Build the registry the same way MongoClientFactory does, including the discriminator
    // registration path (the bug that was fixed — ClassModel.builder must pass conventions).
    final List<Convention> conventions = new ArrayList<>(Conventions.DEFAULT_CONVENTIONS);
    conventions.add(new SecurePropertyConvention(encryptionInstance));

    final PojoCodecProvider.Builder pojoBuilder =
        PojoCodecProvider.builder().conventions(conventions).automatic(true);

    // Register SessionInfo as a discriminator class WITH conventions — this is the path that
    // previously stored eventsJson in plaintext.
    pojoBuilder.register(
        ClassModel.builder(SessionInfo.class)
            .enableDiscriminator(true)
            .conventions(conventions)
            .build());

    final CodecRegistry registry =
        CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders(pojoBuilder.build()),
            MongoClientSettings.getDefaultCodecRegistry());

    // Build a SessionInfo with one event so eventsJson is non-empty.
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setId("session-1");
    sessionInfo.setAppName("test-app");
    sessionInfo.setUserId("user-1");
    sessionInfo.setEvents(List.of(Map.of("id", "event-1", "type", "USER")));

    // Act: encode to a BsonDocument using the registry.
    final Codec<SessionInfo> codec = registry.get(SessionInfo.class);
    final BsonDocument document = new BsonDocument();
    codec.encode(new BsonDocumentWriter(document), sessionInfo, EncoderContext.builder().build());

    // Assert: eventsJson must start with "enc::" — proving encryption was applied.
    assertThat(document.containsKey("eventsJson"))
        .as("document should contain the eventsJson field")
        .isTrue();
    assertThat(document.getString("eventsJson").getValue())
        .as("eventsJson must be encrypted (mocked value)")
        .startsWith("encrypted(");
  }
}

