package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.Repository;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract MongoDB repository implementation providing generic CRUD operations.
 *
 * @param <T> the entity type
 */
public abstract class AbstractMongoRepository<T extends BaseEntity> extends AbstractMongoReadRepository<T>
        implements Repository<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoRepository.class);

    private final ValidationService validationService;

    public AbstractMongoRepository(
            final MongoClientFactory mongoClientFactory,
            final String collectionName,
            final Class<T> entityClass,
            final ValidationService validationService) {
        this(mongoClientFactory, "AGENT_ENGINE", collectionName, entityClass, validationService);
    }

    public AbstractMongoRepository(
            final MongoClientFactory mongoClientFactory,
            final String databaseName,
            final String collectionName,
            final Class<T> entityClass,
            final ValidationService validationService) {
        super(mongoClientFactory, databaseName, collectionName, entityClass);
        this.validationService = validationService;
    }

    @Override
    public T insert(final T entity) {
        try {
            validateEntity(entity);
            if (StringUtils.isBlank(entity.getId())) {
                entity.setId(new ObjectId().toHexString());
            }
            try {
                getCollection().insertOne(entity);
                return entity;
            } catch (final MongoWriteException exception) {
                if (exception.getError().getCode() == 11000) {
                    throw new DuplicateAssetException(entityClass.getSimpleName(), entity.getId());
                }
                LOG.error("Error inserting entity: {}", entity, exception);
                throw new RuntimeException("Error saving entity", exception);
            }
        } catch (final RuntimeException exception) {
            throw exception;
        } catch (final Exception exception) {
            LOG.error("Error saving entity: {}", entity, exception);
            throw new RuntimeException("Error saving entity", exception);
        }
    }

    @Override
    public T update(final String id, final T entity) {
        return updateEntity(id, entity, false);
    }

    @Override
    public T update(final String id, final Update update) {
        try {
            final Bson updateOperation = MongoUtils.toBsonUpdate(update);
            final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
            final T updated = getCollection().findOneAndUpdate(Filters.eq("_id", id), updateOperation, options);
            if (updated != null && validationService != null) {
                validationService.validate(updated);
            }
            return updated;
        } catch (final RuntimeException exception) {
            throw exception;
        } catch (final Exception exception) {
            LOG.error("Error updating entity: {}", id, exception);
            throw new RuntimeException("Error updating entity: " + id, exception);
        }
    }

    @Override
    public T save(final T entity) {
        try {
            if (StringUtils.isBlank(entity.getId())) {
                return insert(entity);
            }
            return updateEntity(entity.getId(), entity, true);
        } catch (final RuntimeException exception) {
            throw exception;
        } catch (final Exception exception) {
            LOG.error("Error saving entity: {}", entity, exception);
            throw new RuntimeException("Error saving entity", exception);
        }
    }

    @Override
    public boolean deleteById(final String id) {
        try {
            final DeleteResult result = getCollection().deleteOne(Filters.eq("_id", id));
            return result.getDeletedCount() > 0;
        } catch (final Exception exception) {
            LOG.error("Error deleting entity by ID: {}", id, exception);
            throw new RuntimeException("Error deleting entity by ID: " + id, exception);
        }
    }

    @Override
    public long deleteByQuery(final Query query) {
        return getCollection().deleteMany(MongoUtils.toBson(query.getFilter())).getDeletedCount();
    }

    private T updateEntity(final String id, final T entity, final boolean upsert) {
        try {
            entity.setId(id);
            validateEntity(entity);
            final ReplaceOptions options = new ReplaceOptions().upsert(upsert);
            final UpdateResult result = getCollection().replaceOne(Filters.eq("_id", entity.getId()), entity, options);
            if (!upsert && result.getMatchedCount() == 0) {
                throw new AssetNotFoundException(entityClass.getSimpleName(), id);
            }
            return entity;
        } catch (final RuntimeException exception) {
            throw exception;
        } catch (final Exception exception) {
            LOG.error("Error saving entity: {}", entity, exception);
            throw new RuntimeException("Error saving entity", exception);
        }
    }

    private void validateEntity(final T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity is required.");
        }
        if (validationService != null) {
            validationService.validate(entity);
        }
    }
}
