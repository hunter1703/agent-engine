package com.agentengine.agent.infra.utils;

/**
 * A persistent, addressable reminder that forms part of the agent's working-memory brief.
 *
 * <p>Reminders are grouped by {@code group} for display (each group becomes a titled section) and
 * individually addressable by {@code id} for targeted removal when the condition they describe is
 * resolved (e.g. a child session is awaited, a knowledge item is no longer relevant).
 *
 * <p>Well-known groups:
 * <ul>
 *   <li>{@code spawned_agents} — child sessions spawned but not yet awaited; id = child session ID
 *   <li>{@code indexed_knowledge} — knowledge IDs auto-indexed and ready to search; id = knowledge ID
 *   <li>{@code active_plan} — current plan state; id = {@code active_plan}
 * </ul>
 *
 * @param group   snake_case category; controls which section this appears under in the brief
 * @param id      unique identifier within the group; used for targeted removal
 * @param message the item text rendered as a bullet under the group's section
 */
public record Reminder(String group, String id, String message) {

    public static final String GROUP_SPAWNED_AGENTS = "spawned_agents";
    public static final String GROUP_INDEXED_KNOWLEDGE = "indexed_knowledge";
    public static final String GROUP_ACTIVE_PLAN = "active_plan";
}
