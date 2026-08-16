package com.agentengine.scheduler.core.validation;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.CronUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import jakarta.inject.Singleton;
import java.time.Instant;

/**
 * Validates a job definition when it is saved, so that a job which could never run is rejected at
 * the point it is scheduled rather than failing at whatever hour it is first due — where, with a
 * failed trigger being terminal, it would simply stop running with nothing to show for it.
 */
@Singleton
public class JobDefinitionValidator implements Validator<JobDefinition> {

  @Override
  public Class<JobDefinition> targetType() {
    return JobDefinition.class;
  }

  @Override
  public void validate(final JobDefinition jobDefinition, final ValidationCollector errors) {
    if (jobDefinition == null || errors == null) {
      return;
    }
    validateJobClass(jobDefinition.getJobClassName(), errors);
    validateCron(jobDefinition.getCronSchedule(), errors);
    validateTags(jobDefinition, errors);
  }

  /** The runner loads the class reflectively and calls a {@link JobContext} constructor. */
  private void validateJobClass(final String jobClassName, final ValidationCollector errors) {
    if (StringUtils.isBlank(jobClassName)) {
      errors.add("jobClassName is required");
      return;
    }
    final Class<?> jobClass;
    try {
      jobClass = Class.forName(jobClassName);
    } catch (final ClassNotFoundException exception) {
      errors.add("jobClassName: no such class " + jobClassName);
      return;
    }
    if (!Job.class.isAssignableFrom(jobClass)) {
      errors.add("jobClassName: " + jobClassName + " does not extend " + Job.class.getName());
      return;
    }
    try {
      jobClass.getConstructor(JobContext.class);
    } catch (final NoSuchMethodException exception) {
      errors.add(
          "jobClassName: "
              + jobClassName
              + " needs a public constructor taking "
              + JobContext.class.getSimpleName());
    }
  }

  private void validateCron(final String cronSchedule, final ValidationCollector errors) {
    if (StringUtils.isBlank(cronSchedule)) {
      errors.add("cronSchedule is required");
      return;
    }
    try {
      CronUtils.validate(cronSchedule);
    } catch (final IllegalArgumentException exception) {
      errors.add("cronSchedule: " + exception.getMessage());
      return;
    }
    // Scheduling bumps the job's version and then writes a trigger. An expression with nothing
    // left to fire — a Quartz cron pinned to a past year, say — writes no trigger, and the scan
    // would then retire the job's previous one as outdated, leaving it with none at all. Rejecting
    // it here keeps the version from moving in the first place.
    if (CronUtils.nextTriggerTime(cronSchedule, Instant.now()).isEmpty()) {
      errors.add("cronSchedule: " + cronSchedule + " has no future occurrence");
    }
  }

  /** A blank tag would silently become a capacity bucket nobody can configure. */
  private void validateTags(final JobDefinition jobDefinition, final ValidationCollector errors) {
    if (CollectionUtils.nullSafeList(jobDefinition.getJobTags()).stream()
        .anyMatch(StringUtils::isBlank)) {
      errors.add("jobTags must not contain blank entries");
    }
  }
}
