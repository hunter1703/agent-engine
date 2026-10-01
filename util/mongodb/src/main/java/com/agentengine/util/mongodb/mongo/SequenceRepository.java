package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;

public class SequenceRepository extends AbstractRepository<Sequence> {

  public SequenceRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(() -> "SEQUENCE", Sequence.class)),
        validationService);
  }

  public long get(final String namespace) {
    final Sequence sequence = findById(namespace);
    return sequence == null ? 0L : sequence.getValue();
  }

  public long increment(final String namespace) {
    return upsertOne(
            Filters.eq(BaseEntity.FIELD_ID, namespace),
            Update.of(Operation.inc(Sequence.FIELD_VALUE, 1L)))
        .getValue();
  }
}
