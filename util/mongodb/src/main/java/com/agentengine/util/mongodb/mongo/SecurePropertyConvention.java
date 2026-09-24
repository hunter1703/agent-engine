package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.Secure;
import com.agentengine.util.crypto.EncryptionService;
import java.lang.annotation.Annotation;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.pojo.ClassModelBuilder;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.PropertyModelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SecurePropertyConvention implements Convention {

  private static final Logger LOG = LoggerFactory.getLogger(SecurePropertyConvention.class);

  private final EncryptionService encryptionService;

  public SecurePropertyConvention(final EncryptionService encryptionService) {
    this.encryptionService = encryptionService;
  }

  @Override
  public void apply(final ClassModelBuilder<?> classModelBuilder) {
    for (final PropertyModelBuilder<?> propertyModelBuilder :
        classModelBuilder.getPropertyModelBuilders()) {

      boolean isSecure = false;
      for (final Annotation annotation :
          CollectionUtils.nullSafeList(propertyModelBuilder.getReadAnnotations())) {
        if (annotation.annotationType().equals(Secure.class)) {
          isSecure = true;
          break;
        }
      }
      if (!isSecure) {
        for (final Annotation annotation :
            CollectionUtils.nullSafeList(propertyModelBuilder.getWriteAnnotations())) {
          if (annotation.annotationType().equals(Secure.class)) {
            isSecure = true;
            break;
          }
        }
      }

      if (!isSecure) {
        continue;
      }
      @SuppressWarnings("unchecked")
      final PropertyModelBuilder<String> stringBuilder =
          (PropertyModelBuilder<String>) propertyModelBuilder;
      stringBuilder.codec(new SecureStringCodec(encryptionService));
    }
  }

  private static final class SecureStringCodec implements Codec<String> {

    private final EncryptionService encryptionService;

    private SecureStringCodec(final EncryptionService encryptionService) {
      this.encryptionService = encryptionService;
    }

    @Override
    public void encode(
        final BsonWriter writer, final String value, final EncoderContext encoderContext) {
      if (value == null) {
        writer.writeNull();
        return;
      }
      if (encryptionService.isEncryptionEnabled()) {
        try {
          writer.writeString(encryptionService.encrypt(value));
        } catch (Exception exception) {
          LOG.error("Failed to encrypt value", exception);
          throw new RuntimeException("Failed to encrypt value", exception);
        }
        return;
      }
      writer.writeString(value);
    }

    @Override
    public String decode(final BsonReader reader, final DecoderContext decoderContext) {
      if (reader.getCurrentBsonType() == BsonType.NULL) {
        reader.readNull();
        return null;
      }

      if (reader.getCurrentBsonType() != BsonType.STRING) {
        return null;
      }

      final String raw = reader.readString();
      try {
        return encryptionService.decrypt(raw);
      } catch (final Exception exception) {
        LOG.error("Failed to decrypt secure value", exception);
        throw new RuntimeException("Failed to decrypt secure value", exception);
      }
    }

    @Override
    public Class<String> getEncoderClass() {
      return String.class;
    }
  }
}
