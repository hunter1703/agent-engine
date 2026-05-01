package com.agentengine.agent.infra.utils;

/**
 * A persistent, addressable notification that forms part of the agent's working-memory brief.
 *
 * <p>Notifications are grouped by {@code group} for display (each group becomes a titled section) and
 * individually addressable by {@code id} for targeted removal when the condition they describe is
 * resolved (e.g. a child session is awaited, a knowledge item is no longer relevant).
 *
 * <p>Well-known keys:
 * <ul>
 *   <li>{@code spawned_agents} — child sessions spawned but not yet awaited; id = child session ID
 *   <li>{@code indexed_knowledge} — knowledge IDs auto-indexed and ready to search; id = knowledge ID
 * </ul>
 *
 * @param group     snake_case category; controls which section this appears under in the brief
 * @param id      unique identifier within the group; used for targeted removal
 * @param message the item text rendered as a bullet under the group's section
 */
public record Notification(String group, String id, String message) {

    public static final String KEY_SPAWNED_AGENTS = "spawned_agents";
    public static final String KEY_INDEXED_KNOWLEDGE = "indexed_knowledge";
}
