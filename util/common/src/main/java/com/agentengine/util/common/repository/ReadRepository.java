package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Generic read-only repository interface.
 *
 * @param <T> the entity type
 */
public interface ReadRepository<T extends BaseEntity> {

  T findById(String id);

  T findById(String id, List<String> includeFields, List<String> excludeFields);

  Map<String, T> findByIds(Collection<String> ids);

  Map<String, T> findByIds(
      Collection<String> ids, List<String> includeFields, List<String> excludeFields);

  PaginatedResult<T> findByQuery(Query query);

  long count();
}
