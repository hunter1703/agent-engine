package com.agentengine.knowledge.jobs;

import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;

/**
 * Runs the indexing pipeline for one knowledge record. Scheduled as a one-off ({@code runAt}) job
 * by {@code KnowledgeServiceImpl} right after a knowledge record is created or its content changes,
 * so indexing runs on the scheduler's workers rather than tying up the knowledge service's own pod.
 * Required payload field: {@code knowledgeId}.
 */
public final class KnowledgeIndexingJob extends Job {

  private static final String KNOWLEDGE_ID_KEY = "knowledgeId";

  private final String knowledgeId;

  public KnowledgeIndexingJob(final JobContext context) {
    super(context);
    this.knowledgeId = requireField(KNOWLEDGE_ID_KEY);
  }

  @Override
  public JobResult run() {
    service(KnowledgeService.class).runIndexing(knowledgeId);
    return JobResult.empty();
  }
}
