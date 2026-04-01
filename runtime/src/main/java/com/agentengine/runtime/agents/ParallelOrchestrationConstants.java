package com.agentengine.runtime.agents;

public final class ParallelOrchestrationConstants {
    private ParallelOrchestrationConstants() {}

    public static final class ViolationCode {
        public static final String POLICY_FALLBACK = "parallel_policy_fallback";

        private ViolationCode() {}
    }

    public static final class DetailKey {
        public static final String STOPPING_POLICY = "stoppingPolicy";
        public static final String AGGREGATION_POLICY = "aggregationPolicy";
        public static final String REQUIRED_SUCCESSES = "requiredSuccesses";
        public static final String SUCCESS_COUNT = "successCount";
        public static final String COMPLETED_COUNT = "completedCount";

        private DetailKey() {}
    }

    public static final class Message {
        public static final String EMPTY_RESULT =
                "Parallel orchestration completed without a successful textual result.";
        public static final String FALLBACK =
                "Parallel stopping target was not met; applied deterministic best-effort fallback.";

        private Message() {}
    }
}
