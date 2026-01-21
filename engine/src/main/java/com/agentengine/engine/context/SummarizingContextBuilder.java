// package com.agentengine.engine.context;
//
// import com.agentengine.engine.message.Message;
// import com.agentengine.engine.message.Role;
// import com.agentengine.engine.model.LLMModel;
// import com.agentengine.engine.api.state.SessionStore;
// import com.agentengine.engine.tools.AgentTool;
//
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.Comparator;
// import java.util.List;
// import java.util.Map;
//
// public final class SummarizingContextBuilder extends BaseContextBuilder {
// private final LLMModel summarizerModel;
// private final long triggerThreshold;
// private final long recencyThreshold;
//
// public SummarizingContextBuilder(
// Message systemMessage,
// Message protocolMessage,
// List<AgentTool> tools,
// String thoughtTag,
// LLMModel summarizerModel,
// double triggerThreshold,
// double recencyThreshold,
// int modelMaxTokens,
// SessionStore stateStore
// ) {
// super(stateStore, systemMessage, protocolMessage, tools, thoughtTag);
// this.summarizerModel = summarizerModel;
// this.triggerThreshold = (long) (triggerThreshold * modelMaxTokens);
// this.recencyThreshold = (long) (recencyThreshold * modelMaxTokens);
// this.modelMaxTokens = modelMaxTokens;
// }
//
// @Override
// public List<Message> buildPrompt(final String sessionId) {
// List<Message> messages = sessionStore.getMessages(sessionId);
// SummaryContext context = ensureSummaries(sessionId, messages);
// List<Message> assembled = new ArrayList<>();
// for (Map<String, Object> summary : context.summaries) {
// String text = String.valueOf(summary.getOrDefault("summary", ""));
// if (!text.isBlank()) {
// assembled.add(Message.system("Conversation summary:\n" + text));
// }
// }
// int startIndex = Math.max(0, context.lastSummaryIndex + 1);
// List<Message> recent = messages.subList(startIndex, messages.size());
// assembled.addAll(recent);
// trimToBudget(state.sessionId(), assembled);
// return super.buildPrompt(new SessionState(state.sessionId(), assembled));
// }
//
// private SummaryContext ensureSummaries(final String sessionId, final
// List<Message> messages) {
// List<Summary> summaries = sortedSummaries(sessionId);
// int lastSummarizedMessageId = summaries.isEmpty() ? "" :
// summaries.getLast().uptoSummarizedMessageId();
//
// int tokensSummaries = countTokensSummaries(summaries);
//
// int tokensMessages = countTokensMessages(messages);
//
// int keepFromIndex = recencyStartIndex(messages, recencyLimit);
//
// if (totalTokens <= triggerLimit) {
// return new SummaryContext(summaries, lastSummaryIndex, keepFromIndex);
// }
// int summarizeUntil = Math.max(keepFromIndex - 1, -1);
// int startIndex = lastSummaryIndex + 1;
// if (summarizeUntil >= startIndex) {
// List<Message> toSummarize = messages.subList(startIndex, summarizeUntil + 1);
// if (!toSummarize.isEmpty()) {
// Message summary = summarizerModel.generate(buildSummaryPrompt(toSummarize));
// String summaryText = summary.getContent();
// int tokens = estimateTokens(summaryText);
// stateStore.addSummary(sessionId, summarizeUntil, summaryText, tokens,
// Instant.now().toString());
// summaries = sortedSummaries(sessionId);
// lastSummaryIndex = summaries.isEmpty() ? -1 :
// asInt(summaries.get(summaries.size()
// - 1).get("upto_idx"), -1);
// }
// }
// return new SummaryContext(summaries, lastSummaryIndex, keepFromIndex);
// }
//
// private void trimToBudget(final String sessionId, final List<Message>
// messages) {
// List<Map<String, Object>> summaries = sortedSummaries(sessionId);
// while (totalTokens(messages, summaries) > modelMaxTokens) {
// if (!summaries.isEmpty()) {
// stateStore.dropOldestSummary(sessionId);
// summaries = sortedSummaries(sessionId);
// } else if (!messages.isEmpty()) {
// stateStore.dropMessagesUntil(sessionId, 1);
// messages.remove(0);
// } else {
// break;
// }
// }
// }
//
// private List<Message> buildSummaryPrompt(final List<Message> messages) {
// String header = "You are a concise meeting recorder. Summarize the following
// conversation
// "
// + "into a compact, factual brief. Preserve key facts, IDs, file paths,
// commands,
// decisions, "
// + "and requirements. Avoid speculation. Keep it readable and short.";
// StringBuilder transcript = new StringBuilder();
// for (Message message : messages) {
// String role = message.getRole().name();
// String content = message.getRole() == Role.ASSISTANT
// ? extractFinalOnly(message.getContent(), thoughtTag())
// : message.getContent();
// transcript.append("[").append(role).append("]
// ").append(content).append("\n");
// }
// return List.of(Message.system(header),
// Message.system(transcript.toString()));
// }
//
// private List<Summary> sortedSummaries(final String sessionId) {
// List<Summary> summaries = new
// ArrayList<>(sessionStore.getSummaries(sessionId));
// summaries.sort(Comparator.comparingInt(summary -> summary.createdTime()));
// return summaries;
// }
//
// private int countTokensSummaries(final List<Summary> summaries) {
// int total = 0;
// for (Summary summary : summaries) {
// total += estimateTokens(summary.content());
// }
// return total;
// }
//
// private int countTokensMessages(final List<Message> messages) {
// int total = 0;
// for (Message message : messages) {
// total += estimateTokens(message.getContent());
// }
// return total;
// }
//
// private int totalTokens(final List<Message> messages, final List<Map<String,
// Object>>
// summaries) {
// return countTokensMessages(messages) + countTokensSummaries(summaries);
// }
//
// private int recencyStartIndex(final List<Message> messages, final int limit)
// {
// int tokens = 0;
// int keepFrom = messages.size();
// for (int index = messages.size() - 1; index >= 0; index--) {
// tokens += estimateTokens(messages.get(index).getContent());
// keepFrom = index;
// if (tokens >= limit) {
// break;
// }
// }
// return keepFrom;
// }
//
// private int estimateTokens(final String text) {
// if (text == null || text.isBlank()) {
// return 1;
// }
// return Math.max(1, (text.length() + 2) / 3);
// }
//
// private record SummaryContext(final List<Map<String, Object>> summaries,
// final int
// lastSummaryIndex, final int keepFromIndex) {
// }
// }
