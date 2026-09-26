package com.agentengine.knowledge.core.pipeline;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.CommunityExpertsService;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every image is normalized before it's sent to the model: downscaled to at most {@link
 * #MAX_DIMENSION} on its long edge and re-encoded as JPEG at a fixed quality, regardless of its
 * original format or size — a page rendered at high DPI, or a photo straight off a camera, would
 * otherwise cost far more time and tokens to describe than the description task needs.
 */
@Singleton
public class MediaServiceImpl implements MediaService {

  private static final Logger LOG = LoggerFactory.getLogger(MediaServiceImpl.class);

  private static final String ANALYZE_PROMPT = "Analyze the attached image.";

  /** Long-edge cap a normalized image is downscaled to fit within, preserving aspect ratio. */
  private static final int MAX_DIMENSION = 1568;

  private static final String NORMALIZED_FORMAT = "jpg";
  private static final String NORMALIZED_MIME_TYPE = "image/jpeg";
  private static final float NORMALIZED_QUALITY = 0.85f;

  private final RuntimeService runtimeService;

  @Inject
  public MediaServiceImpl(final RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @Override
  public ImageDescription describeImage(final Knowledge knowledge, final byte[] imageBytes) {
    final KnowledgeSettings settings = knowledge.getSettings();
    final String visionModelId =
        settings != null && StringUtils.isNotBlank(settings.getVisionModelId())
            ? settings.getVisionModelId()
            : null;

    final byte[] normalized = normalize(imageBytes);
    final UserMessage userMessage =
        new UserMessage(
            List.of(
                new MessagePart.TextPart(ANALYZE_PROMPT),
                new MessagePart.BinaryPart(normalized, NORMALIZED_MIME_TYPE)),
            ResourceGrants.EMPTY);
    final String responseText =
        runtimeService.invokeExpert(
            CommunityExpertsService.VISION_AGENT, visionModelId, userMessage);
    return parseResponse(responseText);
  }

  private static ImageDescription parseResponse(final String responseText) {
    if (StringUtils.isBlank(responseText)) {
      return new ImageDescription("", "");
    }
    try {
      final Map<String, String> parsed =
          JsonUtils.parseJsonPayload(responseText, new TypeReference<>() {});
      return new ImageDescription(
          StringUtils.getOrDefault(parsed.get("description"), ""),
          StringUtils.getOrDefault(parsed.get("ocrText"), ""));
    } catch (final Exception e) {
      LOG.warn(
          "Failed to parse vision agent response as JSON, using raw text as description: {}",
          responseText,
          e);
      return new ImageDescription(responseText.trim(), "");
    }
  }

  /**
   * Decodes {@code imageBytes}, scales it down to fit within {@link #MAX_DIMENSION} if it's larger,
   * flattens it onto an opaque white background (JPEG has no alpha channel), and re-encodes it as
   * JPEG at {@link #NORMALIZED_QUALITY}.
   */
  private static byte[] normalize(final byte[] imageBytes) {
    try {
      final BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (source == null) {
        throw new IllegalArgumentException("No ImageIO reader found for the given image bytes");
      }
      final double scale =
          Math.min(1.0, (double) MAX_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
      final int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
      final int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

      final BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      final Graphics2D graphics = normalized.createGraphics();
      try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(source, 0, 0, width, height, null);
      } finally {
        graphics.dispose();
      }

      return encodeJpeg(normalized);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] encodeJpeg(final BufferedImage image) throws IOException {
    final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(NORMALIZED_FORMAT);
    final ImageWriter writer = writers.next();
    try {
      final ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(NORMALIZED_QUALITY);

      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(out)) {
        writer.setOutput(imageOutputStream);
        writer.write(null, new IIOImage(image, null, null), param);
      }
      return out.toByteArray();
    } finally {
      writer.dispose();
    }
  }
}
