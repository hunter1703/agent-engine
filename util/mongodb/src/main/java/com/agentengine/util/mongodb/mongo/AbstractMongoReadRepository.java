package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.ReadRepository;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract MongoDB repository implementation providing generic read operations.
 *
 * @param <T> the entity type
 */
public abstract class AbstractMongoReadRepository<T extends BaseEntity> implements ReadRepository<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoReadRepository.class);

    private final MongoClient mongoClient;
    protected final String collectionName;
    protected final Class<T> entityClass;
    protected final String databaseName;

    public AbstractMongoReadRepository(
            final MongoClientFactory mongoClientFactory, final String collectionName, final Class<T> entityClass) {
        this(mongoClientFactory, "AGENT_ENGINE", collectionName, entityClass);
    }

    public AbstractMongoReadRepository(
            final MongoClientFactory mongoClientFactory,
            final String databaseName,
            final String collectionName,
            final Class<T> entityClass) {
        this.mongoClient = mongoClientFactory.getClient();
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.entityClass = entityClass;
    }

    @Override
    public T findById(final String id) {
        return findById(id, null, null);
    }

    @Override
    public T findById(final String id, final List<String> includeFields, final List<String> excludeFields) {
        try {
            return getCollection()
                    .find(Filters.eq("_id", id))
                    .projection(MongoUtils.getProjection(includeFields, excludeFields))
                    .first();
        } catch (final Exception exception) {
            LOG.error("Error finding entity by ID: {}", id, exception);
            throw new RuntimeException("Error finding entity by ID: " + id, exception);
        }
    }

    @Override
    public Map<String, T> findByIds(final Collection<String> ids) {
        return findByIds(ids, null, null);
    }

    @Override
    public Map<String, T> findByIds(
            final Collection<String> ids, final List<String> includeFields, final List<String> excludeFields) {
        try {
            final FindIterable<T> iterable = getCollection()
                    .find(Filters.in("_id", ids))
                    .projection(MongoUtils.getProjection(includeFields, excludeFields));
            final Map<String, T> result = new HashMap<>();
            for (final T document : iterable) {
                result.put(document.getId(), document);
            }
            return result;
        } catch (final Exception exception) {
            throw new RuntimeException("Error finding entity by IDs: " + ids, exception);
        }
    }

    @Override
    public PaginatedResult<T> findByQuery(final Query query) {
        try {
            final Page page = query == null || query.getPage() == null ? new Page(0, 20) : query.getPage();
            final List<T> entities = new ArrayList<>();

            final Bson bsonFilter = MongoUtils.toBson(query == null ? null : query.getFilter());
            final Bson bsonSort = MongoUtils.toSortBson(query == null ? null : query.getSort());
            final Bson projection = MongoUtils.toProjectionBson(query);

            FindIterable<T> iterable = getCollection().find(bsonFilter, entityClass);
            if (projection != null) {
                iterable = iterable.projection(projection);
            }
            iterable = iterable.skip(page.getOffset()).limit(page.getLimit());
            if (bsonSort != null) {
                iterable = iterable.sort(bsonSort);
            }

            for (final T document : iterable) {
                entities.add(document);
            }

            final Long total = query != null && query.isIncludeCount() ? count(bsonFilter) : null;
            return PaginatedResult.create(entities, page, total);
        } catch (final Exception exception) {
            LOG.error("Error finding all entities in collection: {} with query: {}", collectionName, query, exception);
            throw new RuntimeException("Error finding all entities in " + collectionName, exception);
        }
    }

    @Override
    public long count() {
        try {
            return getCollection().countDocuments();
        } catch (final Exception exception) {
            LOG.error("Error counting entities", exception);
            throw new RuntimeException("Error counting entities", exception);
        }
    }

    protected final MongoCollection<T> getCollection() {
        return mongoClient.getDatabase(databaseName).getCollection(collectionName, entityClass);
    }

    private long count(final Bson filter) {
        try {
            return getCollection().countDocuments(filter);
        } catch (final Exception exception) {
            LOG.error("Error counting entities with filter", exception);
            return 0;
        }
    }
}
