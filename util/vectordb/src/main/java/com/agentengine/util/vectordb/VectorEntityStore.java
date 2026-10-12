package com.agentengine.util.vectordb;

import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.EntityStore;
import java.util.Map;

/**
 * The vector storage of one entity class, from a {@link VectorBackend}. The value of a semantic
 * search filter, a text or an image, is embedded with the model the filter names, or else the
 * customer's default embedding model for its modality.
 */
public interface VectorEntityStore<T extends VectorEntity> extends EntityStore<T> {

  /**
   * The similarity of each tag of the entities {@code query}, which has one semantic filter,
   * matches, at most {@code maxTags} of them, closest first: the mean similarity of the tag's
   * {@code closestChunksPerTag} closest entities.
   */
  Map<String, Double> findTagSimilarities(Query query, int maxTags, int closestChunksPerTag);
}
