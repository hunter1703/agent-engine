package com.agentengine.knowledge.core.repository;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.core.KnowledgeUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class KnowledgeRepository extends AbstractMongoRepository<Knowledge> {

  @Inject
  public KnowledgeRepository(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(
        mongoClientFactory,
        KnowledgeMongoStoreClientType.KNOWLEDGE,
        Knowledge.class,
        validationService);
  }

  @Override
  protected Query decorateWithPermissionFilter(final Query query) {
    final Map<String, Object> additional =
        CollectionUtils.nullSafeMap(query == null ? null : query.getAdditional());
    final List<String> grants = KnowledgeUtils.readGrants(additional);
    if (CollectionUtils.isEmpty(grants)) {
      return super.decorateWithPermissionFilter(query);
    }
    final Filter permissionFilter = Filters.in(BaseEntity.FIELD_GRANTS, grants);
    final Filter existing = query == null ? null : query.getFilter();
    final Filter combined =
        existing == null ? permissionFilter : Filters.and(existing, permissionFilter);
    return new Query(query).withFilter(combined);
  }
}
