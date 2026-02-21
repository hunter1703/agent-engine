package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.engine.api.update.Operation;
import com.agentengine.engine.api.update.Update;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Updates;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

class MongoUtilsTest {

  @Test
  void toBsonUpdateMapsOperations() {
    final Update update =
        new Update(List.of(Operation.set("name", "value"), Operation.unset("age")));

    final Bson actual = MongoUtils.toBsonUpdate(update);
    final Bson expected = Updates.combine(Updates.set("name", "value"), Updates.unset("age"));

    assertThat(toDocument(actual)).isEqualTo(toDocument(expected));
  }

  @Test
  void toBsonUpdateRequiresUpdate() {
    assertThatThrownBy(() -> MongoUtils.toBsonUpdate(null))
        .isInstanceOf(NullPointerException.class);
  }

  private static BsonDocument toDocument(final Bson bson) {
    return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
  }
}
