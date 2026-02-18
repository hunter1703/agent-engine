package com.agentengine.engine.repository;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.model.InfraConfig;
import com.agentengine.engine.utils.Page;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.engine.utils.Query;
import com.mongodb.client.model.Filters;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;

@Singleton
public class InfraMongoRepository extends AbstractMongoRepository<InfraConfig>{

    public InfraMongoRepository(final MongoClientSupport mongoClientSupport) {
        super(mongoClientSupport, "INFRA", "InfraConfig", InfraConfig.class);
    }

    public <T extends InfraConfig> T findOneByType(String type) {
        //noinspection unchecked
        final PaginatedResult<T> result = (PaginatedResult<T>) super.findByQuery(new Query().withFilter(Filters.eq("type", type)).withPage(new Page(0, 1)));
        return CollectionUtils.getFirst(result.getItems());
    }
}
