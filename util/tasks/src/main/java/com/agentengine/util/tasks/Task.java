package com.agentengine.util.tasks;

/**
 * A stored entity that is also a unit of work: written {@link TaskStatus#PENDING} together with the
 * change that needs processing, and marked {@link TaskStatus#DONE} once processed. Tasks are
 * processed a partition at a time. Two tasks are equal when their ids are, whatever else differs.
 */
public interface Task {

  String FIELD_STATUS = "status";

  /** The task's id, unique within its task type. */
  String getId();

  /** The type of the task, naming the {@link TaskService} that serves it. */
  String taskType();

  /** The task's {@link TaskStatus}, by name. */
  String getStatus();

  void setStatus(String status);

  /** The partition the task belongs to, such as the asset whose access it changes. */
  String partitionId();
}
