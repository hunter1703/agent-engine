package com.agentengine.agent.infra.agents;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.beans.config.ImageClassificationAgentConfig;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.StructuredConcurrencyUtils;
import com.agentengine.util.common.utils.StructuredConcurrencyUtils.TaskOutcome;
import com.agentengine.util.models.embedding.ConfiguredEmbeddingModel;
import com.agentengine.util.models.factories.EmbeddingUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.Part;
import dev.langchain4j.data.message.ImageContent;
import io.reactivex.rxjava3.core.Flowable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope.Subtask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells which tags of the indexed knowledge the attached images belong with.
 *
 * <p>With a {@code modelId}, the agent compares every vector that embedding model finds in an
 * image, such as one per face, with the knowledge indexed with that model; a tag's similarity is the
 * best it gets from any of them. It asks its sub-agents, which are image classification agents
 * too, at the same time, and merges their answers with its own: a tag matches with the probability
 * that at least one of them is right.
 *
 * <p>The answer is a JSON object {@code {"labels": [{label, probability, score}, ...]}}, most
 * probable first.
 */
//TODO: make it better
public class ImageClassificationAgent extends Agent {


  private static final Logger LOG = LoggerFactory.getLogger(ImageClassificationAgent.class);
  private static final int MAX_TAGS = 50;

  private final ImageClassificationAgentConfig config;
  private final List<? extends Agent> classifiers;
  private final KnowledgeService knowledgeService;
  private final ModelProvider modelProvider;
  private final FileService fileService;

  public ImageClassificationAgent(
      final ImageClassificationAgentConfig config,
      final List<? extends Agent> classifiers,
      final KnowledgeService knowledgeService,
      final ModelProvider modelProvider,
      final FileService fileService) {
    super(config.getName(), config.getDescription(), classifiers, config, List.of(), List.of());
    this.config = config;
    this.classifiers = classifiers;
    this.knowledgeService = knowledgeService;
    this.modelProvider = modelProvider;
    this.fileService = fileService;
  }

  @Override
  protected Flowable<Event> runAsyncImpl(final InvocationContext ctx) {
    return Flowable.fromCallable(
        () ->
            Event.builder()
                .author(name())
                .content(
                    Content.fromParts(
                        Part.fromText(JsonUtils.toJson(new Answer(answersMerged(ctx))))))
                .finishReason(new FinishReason(FinishReason.Known.STOP))
                .build());
  }

  @Override
  protected Flowable<Event> runLiveImpl(final InvocationContext ctx) {
    throw new UnsupportedOperationException("Live mode not implemented for image classification");
  }

  private List<LabelMatch> answersMerged(final InvocationContext ctx) {
    final long start = System.nanoTime();
    final List<Callable<List<LabelMatch>>> asks = new ArrayList<>();
    if (StringUtils.isNotBlank(config.getModelId())) {
      asks.add(() -> classify(imageAttachments(ctx)));
    }
    for (final Agent classifier : classifiers) {
      asks.add(() -> answerOf(classifier, ctx));
    }
    final List<List<LabelMatch>> answers = new ArrayList<>();
    Throwable failure = null;
    for (final TaskOutcome<List<LabelMatch>> outcome :
        StructuredConcurrencyUtils.callConcurrentlyUntil("image-classification", asks, _ -> false)) {
      if (outcome.state() == Subtask.State.SUCCESS) {
        answers.add(outcome.value());
      } else {
        failure = outcome.error();
      }
    }
    if (answers.isEmpty() && failure != null) {
      throw ExceptionUtils.wrapInRuntimeException(failure);
    }
    final List<LabelMatch> merged = merge(answers);
    LOG.info("{} answered in {} ms", name(), millisSince(start));
    return merged;
  }

  private List<FileDetails> imageAttachments(final InvocationContext ctx) {
    for (final Event event : ctx.session().events().reversed()) {
      final List<FileDetails> images =
          EventUtils.getAttachments(event).stream()
              .map(AgentFileDetails::toFileDetails)
              .filter(FileUtils::isImageFile)
              .toList();
      if (!images.isEmpty()) {
        return images;
      }
    }
    return List.of();
  }

  /**
   * What this agent's own embedding model finds the images to belong with: for each vector found
   * in them, the tags whose closest indexed images are at least {@code matchThreshold} similar,
   * each rated by the mean similarity of its {@code closestChunksPerTag} closest.
   */
  private List<LabelMatch> classify(final List<FileDetails> images) {
    if (images.isEmpty()) {
      return List.of();
    }
    final long start = System.nanoTime();
    final Map<String, Double> tagVsSimilarity = new HashMap<>();
    try (RefCounted<ConfiguredEmbeddingModel> model =
        modelProvider.getEmbeddingModel(config.getModelId())) {
      long phaseStart = System.nanoTime();
      final List<ImageContent> contents = images.stream().map(this::readImage).toList();
      final long readMillis = millisSince(phaseStart);
      phaseStart = System.nanoTime();
      final List<List<float[]>> imageVectors = EmbeddingUtils.embedImages(model.value(), contents);
      final long embedMillis = millisSince(phaseStart);
      phaseStart = System.nanoTime();
      final List<Callable<Map<String, Double>>> searches =
          imageVectors.stream()
              .flatMap(List::stream)
              .<Callable<Map<String, Double>>>map(
                  vector -> () -> searchTagSimilarities(vector, model.value().modelId()))
              .toList();
      for (final Map<String, Double> found :
          StructuredConcurrencyUtils.callConcurrently("image-vector-search", searches)) {
        found.forEach((tag, similarity) -> tagVsSimilarity.merge(tag, similarity, Math::max));
      }
      final int vectorCount = searches.size();
      LOG.info(
          "{} classified {} image(s) in {} ms: read {} ms, embed {} ms, search {} ms for {} vector(s)",
          name(),
          images.size(),
          millisSince(start),
          readMillis,
          embedMillis,
          millisSince(phaseStart),
          vectorCount);
    }
    return tagVsSimilarity.entrySet().stream()
        .map(
            entry ->
                new LabelMatch(entry.getKey(), probabilityOf(entry.getValue()), entry.getValue()))
        .toList();
  }

  /** The tags whose closest indexed chunks to {@code vector} pass the match threshold, rated. */
  private Map<String, Double> searchTagSimilarities(final float[] vector, final String embedderId) {
    final Query query =
        new Query()
            .withFilter(
                Filters.and(
                    Filters.semanticSearch(
                        KnowledgeChunk.FIELD_MEDIA, vector, config.getMatchThreshold()),
                    Filters.eq(KnowledgeChunk.FIELD_EMBEDDER, embedderId)));
    return knowledgeService.findTagSimilarities(query, MAX_TAGS, config.getClosestChunksPerTag());
  }

  private double probabilityOf(final double similarity) {
    return 1.0
        / (1.0 + Math.exp(-config.getSharpness() * (similarity - config.getMatchThreshold())));
  }

  private ImageContent readImage(final FileDetails image) {
    try (final InputStream content = fileService.getContent(image)) {
      return new ImageContent(
          Base64.getEncoder().encodeToString(content.readAllBytes()), image.mimeType());
    } catch (final IOException e) {
      throw ExceptionUtils.wrapInRuntimeException(e);
    }
  }

  private static long millisSince(final long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }

  private static List<LabelMatch> answerOf(final Agent classifier, final InvocationContext ctx) {
    final List<Event> events = classifier.runAsync(ctx).toList().blockingGet();
    for (final Event event : events.reversed()) {
      final String text = event.content().map(Content::text).orElse(null);
      if (text != null && !text.isBlank()) {
        return JsonUtils.fromJson(text, Answer.class).labels();
      }
    }
    return List.of();
  }

  /** The probability of a tag is that at least one answer is right, and its score the best. */
  private static List<LabelMatch> merge(final List<List<LabelMatch>> answers) {
    final Map<String, Double> labelVsMissProbability = new HashMap<>();
    final Map<String, Double> labelVsScore = new HashMap<>();
    for (final List<LabelMatch> answer : answers) {
      for (final LabelMatch match : answer) {
        labelVsMissProbability.merge(
            match.label(), 1.0 - match.probability(), (miss, other) -> miss * other);
        labelVsScore.merge(match.label(), match.score(), Math::max);
      }
    }
    return labelVsMissProbability.entrySet().stream()
        .map(
            entry ->
                new LabelMatch(
                    entry.getKey(), 1.0 - entry.getValue(), labelVsScore.get(entry.getKey())))
        .sorted(Comparator.comparingDouble(LabelMatch::probability).reversed())
        .toList();
  }

  record LabelMatch(String label, double probability, double score) {}

  record Answer(List<LabelMatch> labels) {}
}
