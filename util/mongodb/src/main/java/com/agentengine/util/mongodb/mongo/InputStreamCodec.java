package com.agentengine.util.mongodb.mongo;

import jakarta.inject.Singleton;
import java.io.InputStream;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

/**
 * Mongo BSON codec for {@link InputStream}. Any input stream fields (e.g. transient streams on
 * records like FileDetails) are written as null and read back as null, preventing codec lookup
 * failures during POJO/record encoding.
 */
@Singleton
public class InputStreamCodec implements Codec<InputStream> {

  @Override
  public void encode(
      final BsonWriter writer, final InputStream value, final EncoderContext encoderContext) {
    writer.writeNull();
  }

  @Override
  public Class<InputStream> getEncoderClass() {
    return InputStream.class;
  }

  @Override
  public InputStream decode(final BsonReader reader, final DecoderContext decoderContext) {
    if (reader.getCurrentBsonType() != BsonType.NULL) {
      reader.skipValue();
    } else {
      reader.readNull();
    }
    return null;
  }
}
