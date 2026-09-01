package com.agentengine.chaos.core.validation;

import com.agentengine.chaos.api.ExperimentDefinition;

/**
 * Answers whether an experiment's fault caused data loss, for the {@code ZERO_DATA_LOSS} success
 * criterion. The real implementation ({@code EventJournalValidator}) queries the event journal for
 * sequence gaps; {@link #noOp()} is a placeholder that always reports no data loss, used until that
 * validator exists.
 */
public interface DataLossChecker {

  boolean dataLossDetected(ExperimentDefinition experiment);

  static DataLossChecker noOp() {
    return experiment -> false;
  }
}
