package com.agentengine.engine.repository.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.Secure;
import com.agentengine.engine.utils.EncryptionService;
import com.mongodb.MongoClientSettings;
import jakarta.enterprise.inject.Instance;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

class SecurePropertyConventionTest {

  @Test
  @SuppressWarnings("unchecked")
  void encryptsSecureFieldsAnnotatedOnFields() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    final EncryptionService encryptionService = mock(EncryptionService.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);
    when(encryptionInstance.get()).thenReturn(encryptionService);
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(encryptionService.encrypt("secret"))
        .thenReturn("encrypted-secret");

    final CodecRegistry registry = buildRegistry(encryptionInstance);
    final Codec<FieldSecureEntity> codec = registry.get(FieldSecureEntity.class);
    final FieldSecureEntity entity = new FieldSecureEntity();
    entity.setSecret("secret");

    final BsonDocument document = new BsonDocument();
    final BsonDocumentWriter writer = new BsonDocumentWriter(document);
    codec.encode(writer, entity, EncoderContext.builder().build());

    assertThat(document.getString("secret").getValue())
        .isEqualTo("encrypted-secret");
  }

  @Test
  @SuppressWarnings("unchecked")
  void encryptsSecureFieldsAnnotatedOnGetters() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    final EncryptionService encryptionService = mock(EncryptionService.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);
    when(encryptionInstance.get()).thenReturn(encryptionService);
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(encryptionService.encrypt("secret"))
        .thenReturn("encrypted-secret");

    final CodecRegistry registry = buildRegistry(encryptionInstance);
    final Codec<GetterSecureEntity> codec = registry.get(GetterSecureEntity.class);
    final GetterSecureEntity entity = new GetterSecureEntity();
    entity.setSecret("secret");

    final BsonDocument document = new BsonDocument();
    final BsonDocumentWriter writer = new BsonDocumentWriter(document);
    codec.encode(writer, entity, EncoderContext.builder().build());

    assertThat(document.getString("secret").getValue())
        .isEqualTo("encrypted-secret");
  }

  @Test
  @SuppressWarnings("unchecked")
  void encryptsSecureFieldsAnnotatedOnSetters() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    final EncryptionService encryptionService = mock(EncryptionService.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);
    when(encryptionInstance.get()).thenReturn(encryptionService);
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(encryptionService.encrypt("secret"))
        .thenReturn("encrypted-secret");

    final CodecRegistry registry = buildRegistry(encryptionInstance);
    final Codec<SetterSecureEntity> codec = registry.get(SetterSecureEntity.class);
    final SetterSecureEntity entity = new SetterSecureEntity();
    entity.setSecret("secret");

    final BsonDocument document = new BsonDocument();
    final BsonDocumentWriter writer = new BsonDocumentWriter(document);
    codec.encode(writer, entity, EncoderContext.builder().build());

    assertThat(document.getString("secret").getValue())
        .isEqualTo("encrypted-secret");
  }

  @Test
  @SuppressWarnings("unchecked")
  void ignoresSecureAnnotationOnNonStringFields() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);

    final CodecRegistry registry = buildRegistry(encryptionInstance);
    final Codec<NonStringSecureEntity> codec = registry.get(NonStringSecureEntity.class);
    final NonStringSecureEntity entity = new NonStringSecureEntity();
    entity.setSecret(42);

    final BsonDocument document = new BsonDocument();
    final BsonDocumentWriter writer = new BsonDocumentWriter(document);
    codec.encode(writer, entity, EncoderContext.builder().build());

    assertThat(document.getInt32("secret").getValue()).isEqualTo(42);
  }

  private CodecRegistry buildRegistry(final Instance<EncryptionService> encryptionInstance) {
    final List<Convention> conventions = new ArrayList<>(Conventions.DEFAULT_CONVENTIONS);
    conventions.add(new SecurePropertyConvention(encryptionInstance));
    return CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(
            PojoCodecProvider.builder()
                .conventions(conventions)
                .automatic(true)
                .build()),
        MongoClientSettings.getDefaultCodecRegistry());
  }

  public static class FieldSecureEntity {
    @Secure private String secret;

    public String getSecret() {
      return secret;
    }

    public void setSecret(final String secret) {
      this.secret = secret;
    }
  }

  public static class NonStringSecureEntity {
    @Secure private Integer secret;

    public Integer getSecret() {
      return secret;
    }

    public void setSecret(final Integer secret) {
      this.secret = secret;
    }
  }

  public static class GetterSecureEntity {
    private String secret;

    @Secure
    public String getSecret() {
      return secret;
    }

    public void setSecret(final String secret) {
      this.secret = secret;
    }
  }

  public static class SetterSecureEntity {
    private String secret;

    public String getSecret() {
      return secret;
    }

    @Secure
    public void setSecret(final String secret) {
      this.secret = secret;
    }
  }
}
