package com.agentengine.util.agents;

public final class Constants {

  // TODO: categorize constants later on
  public static final String HITL_TOOL_NAME = "human_in_the_loop";
  public static final String AWAIT_AGENT_TOOL_NAME = "await_agent";
  public static final String SPAWN_AGENT_TOOL_NAME = "spawn_agent";
  public static final String SEND_MESSAGE_TOOL_NAME = "send_message";
  public static final String AGENT_TRANSFER_TOOL_NAME = "transfer_to_agent";
  public static final String SAVE_ANSWER_TOOL_NAME = "save_answer";
  public static final String CREATE_PLAN_TOOL_NAME = "create_plan";
  public static final String UPDATE_PLAN_TOOL_NAME = "update_plan";
  public static final String ADD_TASK_TOOL_NAME = "add_task";
  public static final String UPDATE_TASK_INFO_TOOL_NAME = "update_task_info";
  public static final String START_TASK_TOOL_NAME = "start_task";
  public static final String COMPLETE_TASK_TOOL_NAME = "complete_task";
  public static final String FINISH_PLAN_TOOL_NAME = "finish_plan";
  public static final String ARG_ORIGINAL_FUNCTION_CALL = "originalFunctionCall";
  public static final String AUTHOR_USER = "user";
  public static final String ARG_TOOL_CONFIRMATION = "toolConfirmation";
  public static final String ARG_KNOWLEDGE_ID = "knowledgeId";
  public static final String ID_SEPARATOR = ":";
  public static final String ARG_CHILD_SESSION_ID = "child_session_id";
  public static final String ARG_AWAIT_COMPLETION = "await_completion";
  public static final String ARG_GOAL = "goal";
  public static final String CREATE_NOTEBOOK_TOOL_NAME = "create_notebook";
  public static final String CREATE_NOTE_TOOL_NAME = "create_note";
  public static final String READ_NOTE_TOOL_NAME = "read_note";
  public static final String DELETE_NOTE_TOOL_NAME = "delete_note";
  public static final String DELETE_NOTEBOOK_TOOL_NAME = "delete_notebook";
  public static final String ARG_NOTEBOOK_ID = "notebook_id";
  public static final String ARG_NOTEBOOK_DESCRIPTION = "description";
  public static final String ARG_NOTE_TITLE = "note_title";
  public static final String ARG_CONTINUATION = "continuation";
  public static final String ARG_KNOWLEDGE_IDS = "knowledge_ids";
  public static final String ARG_KNOWLEDGE_SOURCES = "knowledge_sources";
  public static final String ARG_NOTEBOOK_GRANTS = "notebook_grants";
  public static final String READ_KNOWLEDGE_SOURCE_TOOL_NAME = "read_knowledge_source";
  public static final String ARG_SOURCE = "source";
  public static final String SEARCH_KNOWLEDGE_TOOL_NAME = "search_knowledge";
  public static final String NOTEBOOK_TOOLSET_NAME = "notebook";

  private Constants() {}
}
