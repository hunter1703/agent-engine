package com.agentengine.agent.infra.guardrails;

import static com.agentengine.util.common.JsonUtils.parseJsonPayload;

import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.StructuredConcurrencyUtils;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelevanceScorer {
    private static final Logger LOG = LoggerFactory.getLogger(RelevanceScorer.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final long MODEL_TIMEOUT_SECONDS = 8L;
    private static final double MODEL_UNAVAILABLE_SCORE = 1D;

    private final ModelProvider modelProvider;
    private final String modelId;

    public RelevanceScorer(final ModelProvider modelProvider, final String modelId) {
        this.modelProvider = modelProvider;
        this.modelId = modelId;
    }

    public double score(final String anchor, final String candidate) {
        final double llmScore = scoreInternal(anchor, candidate);
        return llmScore < 0 ? MODEL_UNAVAILABLE_SCORE : llmScore;
    }

    private double scoreInternal(final String anchor, final String candidate) {
        if (StringUtils.isBlank(candidate)) {
            return -1D;
        }
        if (StringUtils.isBlank(anchor)) {
            return 1D;
        }
        try {
            final List<Integer> scores = StructuredConcurrencyUtils.runConcurrently(List.of(
                    () -> askScore(relevancePrompt(anchor, candidate)),
                    () -> askScore(irrelevancePrompt(anchor, candidate))));
            final int x = clampScore(scores.get(0));
            final int y = clampScore(scores.get(1));
            final double combined = (x + (MAX_SCORE - y)) / 2.0;
            return clamp(combined / MAX_SCORE, 0D, 1D);
        } catch (Exception ex) {
            LOG.debug("LLM-based relevance evaluation failed; returning unavailable score.", ex);
            return -1D;
        }
    }

    private int askScore(final String prompt) {
        final LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.builder()
                        .role(Constants.AUTHOR_USER)
                        .parts(Part.fromText(prompt))
                        .build()))
                .build();
        final LlmResponse response = modelProvider
                .invokeAcquiring(modelId, model -> model.generateContent(request, false))
                .timeout(MODEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .blockingSingle();
        final String text = response.content().map(Content::text).orElse("");
        return parseScore(text);
    }

    private static int parseScore(final String raw) {
        if (StringUtils.isBlank(raw)) {
            return 0;
        }
        final Map<String, Object> parsed = parseJsonPayload(raw);
        if (parsed != null && parsed.containsKey("score")) {
            final Double value = CollectionUtils.getDoubleValueFromMap(parsed, "score");
            if (value != null) {
                return clampScore((int) Math.round(value));
            }
        }
        final Matcher matcher = NUMBER_PATTERN.matcher(raw);
        if (matcher.find()) {
            try {
                return clampScore((int) Math.round(Double.parseDouble(matcher.group(1))));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int clampScore(final int value) {
        if (value < MIN_SCORE) {
            return MIN_SCORE;
        }
        return Math.min(value, MAX_SCORE);
    }

    private static double clamp(final double value, final double min, final double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static String relevancePrompt(final String anchor, final String candidate) {
        return """
        Evaluate how relevant the response is to the user intent.
        Intent:
        %s

        Response:
        %s

        Return strict JSON only: {"score": <0..100 integer>}
        """.formatted(anchor, candidate);
    }

    private static String irrelevancePrompt(final String anchor, final String candidate) {
        return """
        Evaluate how irrelevant the response is to the user intent.
        Intent:
        %s

        Response:
        %s

        Return strict JSON only: {"score": <0..100 integer>}
        """.formatted(anchor, candidate);
    }
}
