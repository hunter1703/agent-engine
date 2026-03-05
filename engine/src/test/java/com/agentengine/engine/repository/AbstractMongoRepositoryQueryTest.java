package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.query.Page;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.util.List;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

class AbstractMongoRepositoryQueryTest {

  @Test
  void findByQuerySkipsTotalCountWhenPaginationMetadataNotRequested() {
    final MongoCollection<TestEntity> collection = mockCollectionWithSingleEntity();
    final TestRepository repository = new TestRepository(collection);

    final Query query = new Query();
    final PaginatedResult<TestEntity> result = repository.findByQuery(query);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getTotal()).isNull();
    verify(collection, never()).countDocuments(any(Bson.class));
  }

  @Test
  void findByQueryComputesTotalWhenPaginationMetadataRequested() {
    final MongoCollection<TestEntity> collection = mockCollectionWithSingleEntity();
    when(collection.countDocuments(any(Bson.class))).thenReturn(1L);
    final TestRepository repository = new TestRepository(collection);

    final Query query = new Query();
    query.setPage(new Page(0, 10));
    final PaginatedResult<TestEntity> result = repository.findByQuery(query);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getTotal()).isEqualTo(1L);
    verify(collection).countDocuments(any(Bson.class));
  }

  @Test
  void findByQueryAppliesProjectionFromQueryFields() {
    final MongoCollection<TestEntity> collection = mock(MongoCollection.class);
    final FindIterable<TestEntity> iterable = mock(FindIterable.class);
    final MongoCursor<TestEntity> cursor = mock(MongoCursor.class);
    final TestEntity entity = new TestEntity();
    entity.setId("id-1");
    when(collection.find(any(Bson.class), eq(TestEntity.class))).thenReturn(iterable);
    when(iterable.projection(any(Bson.class))).thenReturn(iterable);
    when(iterable.skip(anyInt())).thenReturn(iterable);
    when(iterable.limit(anyInt())).thenReturn(iterable);
    when(iterable.sort(any(Bson.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(cursor);
    when(cursor.hasNext()).thenReturn(true, false);
    when(cursor.next()).thenReturn(entity);

    final TestRepository repository = new TestRepository(collection);
    final Query query = new Query().withExcludeFields(List.of("sessionInfo.eventsJson"));

    repository.findByQuery(query);

    verify(iterable).projection(any(Bson.class));
  }

  @SuppressWarnings("unchecked")
  private static MongoCollection<TestEntity> mockCollectionWithSingleEntity() {
    final MongoCollection<TestEntity> collection = mock(MongoCollection.class);
    final FindIterable<TestEntity> iterable = mock(FindIterable.class);
    final TestEntity entity = new TestEntity();
    entity.setId("id-1");

    when(collection.find(any(Bson.class), eq(TestEntity.class))).thenReturn(iterable);
    when(iterable.skip(anyInt())).thenReturn(iterable);
    when(iterable.limit(anyInt())).thenReturn(iterable);
    when(iterable.sort(any(Bson.class))).thenReturn(iterable);
    when(iterable.projection(any(Bson.class))).thenReturn(iterable);
    final MongoCursor<TestEntity> cursor = mock(MongoCursor.class);
    when(cursor.hasNext()).thenReturn(true, false);
    when(cursor.next()).thenReturn(entity);
    when(iterable.iterator()).thenReturn(cursor);
    return collection;
  }

  private static final class TestRepository extends AbstractMongoRepository<TestEntity> {
    private final MongoCollection<TestEntity> collection;

    private TestRepository(final MongoCollection<TestEntity> collection) {
      super(mock(MongoClientFactory.class), "Test", TestEntity.class);
      this.collection = collection;
    }

    @Override
    protected MongoCollection<TestEntity> getCollection() {
      return collection;
    }
  }

  private static final class TestEntity extends BaseEntity {}
}
