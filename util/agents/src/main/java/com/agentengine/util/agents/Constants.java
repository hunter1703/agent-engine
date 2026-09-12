package com.agentengine.util.agents;

public interface Constants {

  interface ToolNames {
    String HITL = "human_in_the_loop";
    String AWAIT_AGENT = "await_agent";
    String SPAWN_AGENT = "spawn_agent";
    String SEND_MESSAGE = "send_message";
    String AGENT_TRANSFER = "transfer_to_agent";
    String SAVE_ANSWER = "save_answer";
    String CREATE_PLAN = "create_plan";
    String UPDATE_PLAN = "update_plan";
    String ADD_TASK = "add_task";
    String UPDATE_TASK_INFO = "update_task_info";
    String START_TASK = "start_task";
    String COMPLETE_TASK = "complete_task";
    String FINISH_PLAN = "finish_plan";
    String CREATE_NOTEBOOK = "create_notebook";
    String CREATE_NOTE = "create_note";
    String READ_NOTE = "read_note";
    String DELETE_NOTE = "delete_note";
    String DELETE_NOTEBOOK = "delete_notebook";
    String READ_KNOWLEDGE_SOURCE = "read_knowledge_source";
    String SEARCH_KNOWLEDGE = "search_knowledge";

    static boolean isAgentRoutingTool(String toolName) {
      return SPAWN_AGENT.equals(toolName)
          || SEND_MESSAGE.equals(toolName)
          || AWAIT_AGENT.equals(toolName);
    }
  }

  interface ToolArgs {
    String ORIGINAL_FUNCTION_CALL = "originalFunctionCall";
    String TOOL_CONFIRMATION = "toolConfirmation";
    String KNOWLEDGE_ID = "knowledgeId";
    String CHILD_SESSION_ID = "child_session_id";
    String AWAIT_COMPLETION = "await_completion";
    String GOAL = "goal";
    String NOTEBOOK_ID = "notebook_id";
    String NOTEBOOK_DESCRIPTION = "description";
    String NOTE_TITLE = "note_title";
    String CONTINUATION = "continuation";
    String KNOWLEDGE_IDS = "knowledge_ids";
    String KNOWLEDGE_SOURCES = "knowledge_sources";
    String NOTEBOOK_GRANTS = "notebook_grants";
    String NOTE_GRANTS = "note_grants";
    String SOURCE = "source";
  }

  interface Toolsets {
    String NOTEBOOK = "notebook";
  }

  String AUTHOR_USER = "user";
  String ID_SEPARATOR = ":";
}
