package com.agentengine.agent.api.services;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.tenancy.AssetPermissionService;
import com.agentengine.util.agents.beans.AgentSchedule;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;
import com.agui.community.core.event.Event;
import java.util.Collection;
import java.util.Map;
import org.reactivestreams.Publisher;

@MicroService("agent")
public interface RuntimeService extends AssetPermissionService {

  String RUNNER_CACHE = "session-runner-cache";

  /**
   * Initialises the session actor and enqueues the message. Returns immediately with the resolved
   * session ID; the agent runs in the background. Prefer {@link #invoke} if the event stream is not
   * needed.
   */
  Publisher<SessionEvent> startSession(String agentId, String sessionId, UserMessage userMessage);

  /** Like {@link #startSession}, but maps the raw runtime stream to AG-UI protocol events */
  Publisher<Event> startSessionAgui(String agentId, String sessionId, UserMessage userMessage);

  /** Like {@link #startSession}, for a caller with no interest in the event stream. */
  String invoke(String agentId, String sessionId, UserMessage userMessage);

  /**
   * Answers a pending interrupt on the session actor. Returns immediately; the run's continuation
   * events are delivered via {@link #subscribeToSession}.
   */
  void resumeSession(String sessionId, ResumeRequest resumeRequest);

  /**
   * Returns a publisher that emits committed history, then uncommitted current-turn events, then
   * live events, and completes when the terminal event is received. Safe to call from multiple
   * concurrent subscribers for the same session.
   */
  Publisher<SessionEvent> subscribeToSession(String sessionId, boolean liveOnly);

  /** Like {@link #subscribeToSession}, mapped to AG-UI protocol events. */
  Publisher<Event> subscribeToSessionAgui(String sessionId, boolean liveOnly);

  void rollbackSession(String sessionId, String runId);

  String invokeExpert(String expertId, String modelId, UserMessage userMessage);

  /** Creates or replaces an agent schedule, and the scheduler job that fires it. */
  AgentSchedule saveSchedule(AgentSchedule schedule);

  AgentSchedule getSchedule(String id);

  Map<String, AgentSchedule> getSchedules(Collection<String> ids);

  PaginatedResult<AgentSchedule> findSchedules(Query query);

  /** Deletes an agent schedule and cancels its job. False if there was no such schedule. */
  boolean deleteSchedule(String id);

  void deleteAgentSchedules(Collection<String> agentIds);
}
