package com.agentengine.util.models.embedding;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;

import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic embedding model for image embedding services or HTTP endpoints (e.g., ArcFace, SigLIP).
 *
 * <p>Sends base64-encoded images to an OpenAI-compatible {@code /embeddings} endpoint, which
 * answers with zero or more embeddings per image, each tagged with the index of its image. As an
 * {@link dev.langchain4j.model.embedding.EmbeddingModel} it yields every vector found in the images
 * of a request, one embedding each, so a request has no embedding for an image the endpoint found
 * nothing to embed in, and several for an image it found several in.
 */
public class ImageEmbeddingModel extends DimensionAwareEmbeddingModel {

  private final HttpClient httpClient;
  private final URI endpoint;
  private final String modelName;
  private final int dimensions;
  private final String apiKey;
  private final Duration timeout;
  private final int maxRetries;

  private ImageEmbeddingModel(final Builder builder) {
    this.endpoint = URI.create(normalize(builder.baseUrl));
    this.modelName = builder.modelName;
    this.dimensions = builder.dimensions;
    this.apiKey = builder.apiKey;
    this.timeout = getOrDefault(builder.timeout, ofSeconds(30));
    this.maxRetries = getOrDefault(builder.maxRetries, 2);
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(ofSeconds(2))
            .build();
  }

  @Override
  protected Integer knownDimension() {
    return dimensions;
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public Set<ContentType> supportedContentTypes() {
    return Set.of(ContentType.IMAGE);
  }

  @Override
  public EmbeddingResponse doEmbed(final EmbeddingRequest request) {
    final String effectiveModel = getOrDefault(request.parameters().modelName(), modelName);
    final List<String> images =
        request.inputs().stream().map(ImageEmbeddingModel::base64Of).toList();
    final List<Embedding> embeddings =
        embed(images, effectiveModel).stream().flatMap(List::stream).map(Embedding::from).toList();

    return EmbeddingResponse.builder()
        .embeddings(embeddings)
        .metadata(EmbeddingResponseMetadata.builder().modelName(effectiveModel).build())
        .build();
  }

  private List<List<float[]>> embed(final List<String> base64Images, final String model) {
    final HttpRequest httpRequest = buildHttpRequest(base64Images, model);
    final HttpResponse<InputStream> response =
        withRetryMappingExceptions(() -> send(httpRequest), maxRetries);
    return parseVectors(response, base64Images.size());
  }

  private HttpResponse<InputStream> send(final HttpRequest request) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (final IOException e) {
      throw new RuntimeException("Failed to reach image embedding server at " + endpoint, e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while calling image embedding server", e);
    }
  }

  private HttpRequest buildHttpRequest(final List<String> base64Images, final String model) {
    final Map<String, Object> body = new HashMap<>();
    body.put("model", model);
    body.put("input", base64Images);
    final HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json");

    if (StringUtils.isNotBlank(apiKey)) {
      builder.header("Authorization", "Bearer " + apiKey);
    }

    return builder
        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body), StandardCharsets.UTF_8))
        .build();
  }

  private static String base64Of(final EmbeddingInput input) {
    if (input.contents().isEmpty()
        || !(input.contents().getFirst() instanceof ImageContent imageContent)) {
      throw new IllegalArgumentException("ImageEmbeddingModel requires ImageContent input");
    }
    return base64Of(imageContent);
  }

  private static String base64Of(final ImageContent imageContent) {
    final String base64 = imageContent.image().base64Data();
    if (StringUtils.isBlank(base64)) {
      throw new IllegalArgumentException(
          "ImageEmbeddingModel requires images carrying base64Data; URL-based images are not supported");
    }
    return base64;
  }

  private static List<List<float[]>> parseVectors(
      final HttpResponse<InputStream> response, final int imageCount) {
    try (final InputStream bodyStream = response.body()) {
      if (response.statusCode() != 200) {
        final String errorBody = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        throw new RuntimeException(
            "Image embedding server returned HTTP " + response.statusCode() + ": " + errorBody);
      }

      final OpenAiEmbeddingResponse body =
          JsonUtils.fromStream(bodyStream, new TypeReference<>() {});

      if (body == null || body.data() == null) {
        throw new RuntimeException("Image embedding server response missing 'data'");
      }

      final List<List<float[]>> vectorsOfImage = new ArrayList<>(imageCount);
      for (int i = 0; i < imageCount; i++) {
        vectorsOfImage.add(new ArrayList<>());
      }
      for (final OpenAiEmbeddingData data : body.data()) {
        if (data.embedding() == null
            || data.index() == null
            || data.index() < 0
            || data.index() >= imageCount) {
          throw new RuntimeException("Image embedding server response has an invalid entry");
        }
        final float[] vector = new float[data.embedding().size()];
        for (int i = 0; i < vector.length; i++) {
          final Float value = data.embedding().get(i);
          if (value == null) {
            throw new RuntimeException("Null value in image embedding at index " + i);
          }
          vector[i] = value;
        }
        vectorsOfImage.get(data.index()).add(vector);
      }
      return vectorsOfImage;
    } catch (final IOException e) {
      throw ExceptionUtils.wrapInRuntimeException(e);
    }
  }

  private static String normalize(final String baseUrl) {
    if (StringUtils.isBlank(baseUrl)) {
      throw new IllegalArgumentException("baseUrl must not be blank");
    }
    final String trimmed = baseUrl.trim();
    final String withoutTrailingSlash =
        trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    return withoutTrailingSlash.endsWith("/embeddings")
        ? withoutTrailingSlash
        : withoutTrailingSlash + "/embeddings";
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer dimensions;
    private Duration timeout;
    private Integer maxRetries;

    public Builder baseUrl(final String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder apiKey(final String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public Builder modelName(final String modelName) {
      this.modelName = modelName;
      return this;
    }

    public Builder dimensions(final int dimensions) {
      this.dimensions = dimensions;
      return this;
    }

    public Builder timeout(final Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder maxRetries(final int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public ImageEmbeddingModel build() {
      return new ImageEmbeddingModel(this);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OpenAiEmbeddingResponse(@JsonProperty("data") List<OpenAiEmbeddingData> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OpenAiEmbeddingData(
      @JsonProperty("index") Integer index, @JsonProperty("embedding") List<Float> embedding) {}
}
