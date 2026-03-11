package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.config.PaginationType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class PaginationStrategyRegistry {

  private final Map<PaginationType, PaginationStrategy> strategies;

  @Inject
  public PaginationStrategyRegistry() {
    this(
        List.of(
            new NoPaginationStrategy(),
            new PagePaginationStrategy(),
            new OffsetPaginationStrategy(),
            new CursorPaginationStrategy(),
            new NextPageUrlPaginationStrategy()));
  }

  public PaginationStrategyRegistry(final Collection<PaginationStrategy> customStrategies) {
    final Map<PaginationType, PaginationStrategy> mutableMap = new EnumMap<>(PaginationType.class);
    customStrategies.forEach(strategy -> mutableMap.put(strategy.type(), strategy));
    strategies = Map.copyOf(mutableMap);
  }

  public PaginationStrategy resolve(final PaginationType type) {
    final PaginationType normalizedType = type == null ? PaginationType.NONE : type;
    return strategies.getOrDefault(normalizedType, strategies.get(PaginationType.NONE));
  }
}
