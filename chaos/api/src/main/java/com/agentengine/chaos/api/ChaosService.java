package com.agentengine.chaos.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ChaosService {

  CompletionStage<ExperimentResult> executeExperiment(ExperimentDefinition experiment);

  CompletionStage<Void> scheduleExperiment(ExperimentDefinition experiment, String cronExpression);

  CompletionStage<Void> emergencyStop(String experimentId);

  List<ExperimentResult> getExperimentHistory(String targetSelector);
}
