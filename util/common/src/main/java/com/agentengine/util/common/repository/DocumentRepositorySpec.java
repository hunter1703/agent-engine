package com.agentengine.util.common.repository;

import com.agentengine.util.common.beans.BaseEntity;

/**
 * What a repository registers with a {@link DocumentBackend}: where its entity class is kept — in
 * the {@code clientType} database of each customer, or in a single {@code global} one shared by
 * every customer.
 */
public record DocumentRepositorySpec<T extends BaseEntity>(
    DocumentStoreClientType clientType, Class<T> entityClass, boolean global) {

  /** A collection in each customer's own database. */
  public static <T extends BaseEntity> DocumentRepositorySpec<T> perCustomer(
      final DocumentStoreClientType clientType, final Class<T> entityClass) {
    return new DocumentRepositorySpec<>(clientType, entityClass, false);
  }

  /** A collection in the one database shared by every customer. */
  public static <T extends BaseEntity> DocumentRepositorySpec<T> global(
      final DocumentStoreClientType clientType, final Class<T> entityClass) {
    return new DocumentRepositorySpec<>(clientType, entityClass, true);
  }
}
