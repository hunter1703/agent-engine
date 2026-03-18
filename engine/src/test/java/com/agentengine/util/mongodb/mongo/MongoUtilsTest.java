package com.agentengine.util.mongodb.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Operator;
import com.mongodb.MongoClientSettings;
import java.util.List;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class MongoUtilsTest {

  @Test
  void shouldMapIdFilterToMongoIdField() {
    final Filter filter = new Filter().withField("id").withOp(Operator.EQ);
    filter.setValues(List.of("agent-123"));

    final BsonDocument bson = MongoUtils.toBson(filter)
        .toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());

    assertThat(bson).isEqualTo(BsonDocument.parse("{\"_id\": \"agent-123\"}"));
  }

  @Test
  void shouldKeepNonIdFieldNamesUnchanged() {
    final Filter filter = new Filter().withField("name").withOp(Operator.EQ);
    filter.setValues(List.of("Agent Name"));

    final BsonDocument bson = MongoUtils.toBson(filter)
        .toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());

    assertThat(bson).isEqualTo(BsonDocument.parse("{\"name\": \"Agent Name\"}"));
  }
}
