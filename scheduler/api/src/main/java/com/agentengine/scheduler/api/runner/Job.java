package com.agentengine.scheduler.api.runner;

/**
 * A unit of scheduled work. Implementations are instantiated per run and must declare a public
 * constructor taking a {@link JobContext}.
 */
public abstract class Job {

    protected final JobContext context;

    protected Job(final JobContext context) {
        this.context = context;
    }

    /** Runs the job. Never returns null; use {@link JobResult#empty()} when there is nothing to carry forward. */
    public abstract JobResult run();
}
