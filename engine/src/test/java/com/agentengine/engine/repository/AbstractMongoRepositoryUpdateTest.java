package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.update.Operation;
import com.agentengine.engine.api.update.Update;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbstractMongoRepositoryUpdateTest {

  @Test
  void updateAppliesMongoUpdateOperations() {
    final MongoCollection<TestEntity> collection = mock(MongoCollection.class);
    final TestEntity updated = new TestEntity();
    updated.setId("id");
    when(collection.findOneAndUpdate(any(Bson.class), any(Bson.class), any(FindOneAndUpdateOptions.class)))
        .thenReturn(updated);
    final TestRepository repository = new TestRepository(collection);

    final Update update = new Update(List.of(Operation.set("name", "value"), Operation.unset("age")));

    final TestEntity result = repository.update("id", update);

    assertThat(result).isSameAs(updated);

    final ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
    final ArgumentCaptor<Bson> updateCaptor = ArgumentCaptor.forClass(Bson.class);
    final ArgumentCaptor<FindOneAndUpdateOptions> optionsCaptor = ArgumentCaptor
        .forClass(FindOneAndUpdateOptions.class);

    verify(collection).findOneAndUpdate(filterCaptor.capture(), updateCaptor.capture(), optionsCaptor.capture());

    final Bson expectedFilter = Filters.eq("_id", "id");
    final Bson expectedUpdate = Updates.combine(Updates.set("name", "value"), Updates.unset("age"));

    assertThat(toDocument(filterCaptor.getValue())).isEqualTo(toDocument(expectedFilter));
    assertThat(toDocument(updateCaptor.getValue())).isEqualTo(toDocument(expectedUpdate));
    assertThat(optionsCaptor.getValue().getReturnDocument()).isEqualTo(ReturnDocument.AFTER);
  }

  private static BsonDocument toDocument(final Bson bson) {
    return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
  }

  private static final class TestRepository extends AbstractMongoRepository<TestEntity> {
    private final MongoCollection<TestEntity> collection;

    private TestRepository(final MongoCollection<TestEntity> collection) {
      super(mock(MongoClientSupport.class), "Test", TestEntity.class);
      this.collection = collection;
    }

    @Override
    protected MongoCollection<TestEntity> getCollection() {
      return collection;
    }
  }

  private static final class TestEntity extends BaseEntity {
  }
}
