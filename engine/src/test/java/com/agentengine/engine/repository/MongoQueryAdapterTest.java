package com.agentengine.engine.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.agentengine.engine.api.query.Sort;
import com.agentengine.engine.api.query.Sort.Order;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

public class MongoQueryAdapterTest {

  @Test
  public void testToSortBson_NullSort() {
    assertNull(MongoQueryAdapter.toSortBson(null));
  }

  @Test
  public void testToSortBson_NullField() {
    Sort sort = new Sort(null, Sort.Order.ASC);
    assertNull(MongoQueryAdapter.toSortBson(sort));
  }

  @Test
  public void testToSortBson_Ascending() {
    Sort sort = new Sort("name", Sort.Order.ASC);
    Bson bson = MongoQueryAdapter.toSortBson(sort);
    
    assertNotNull(bson);
    BsonDocument doc = bson.toBsonDocument(BsonDocument.class, null);
    assertEquals(1, doc.getInt32("name").getValue());
  }

  @Test
  public void testToSortBson_Descending() {
    Sort sort = new Sort("age", Sort.Order.DESC);
    Bson bson = MongoQueryAdapter.toSortBson(sort);

    assertNotNull(bson);
    BsonDocument doc = bson.toBsonDocument(BsonDocument.class, null);
    assertEquals(-1, doc.getInt32("age").getValue());
  }

  @Test
  public void testToSortBson_UnknownDefaultsToAscending() {
    Sort sort = new Sort("status", Order.UNKNOWN);
    Bson bson = MongoQueryAdapter.toSortBson(sort);

    assertNotNull(bson);
    BsonDocument doc = bson.toBsonDocument(BsonDocument.class, null);
    assertEquals(1, doc.getInt32("status").getValue());
  }
}
