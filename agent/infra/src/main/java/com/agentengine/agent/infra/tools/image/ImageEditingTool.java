package com.agentengine.agent.infra.tools.image;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Part;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all image editing tools.
 *
 * <p>Subclasses call {@link #processImage(String, Function, ToolContext)} from their {@code
 * execute} method, passing the input artifact name and a function that receives the downloaded temp
 * file and returns the processed output temp file. This class handles everything else: artifact
 * loading, format detection, temp file lifecycle, and saving the result as an ADK artifact.
 */
public abstract class ImageEditingTool extends Tool {

  private static final Logger LOG = LoggerFactory.getLogger(ImageEditingTool.class);

  protected ImageEditingTool(final ToolDescriptor descriptor) {
    super(descriptor, false);
  }

  /**
   * Loads the artifact at {@code artifactName}, applies {@code adjustment}, and saves the result as
   * a new ADK artifact so the model can view it via {@code load_artifacts}.
   *
   * <p>Returns {@code { artifactName }} on success or {@code { error }} on failure.
   *
   * @param artifactName artifact key of the input image
   * @param adjustment function that receives the input temp file and returns the processed output
   *     temp file
   * @param toolContext used to load the input and save the output artifact
   */
  protected ToolOutput<Map<String, Object>> processImage(
      final String artifactName,
      final Function<File, File> adjustment,
      final ToolContext toolContext) {
    try {
      final Part input = toolContext.loadArtifact(artifactName, Optional.empty()).blockingGet();
      if (input == null) {
        return ToolOutput.direct(Map.of("error", "Artifact not found: " + artifactName));
      }
      final byte[] inputBytes =
          input
              .inlineData()
              .orElseThrow(() -> new IllegalArgumentException("Artifact has no inline data."))
              .data()
              .orElseThrow(() -> new IllegalArgumentException("Artifact data is empty."));
      final String mimeType = input.inlineData().get().mimeType().orElse("image/png");
      final String format =
          mimeType.contains("/") ? mimeType.substring(mimeType.lastIndexOf('/') + 1) : "png";
      final File inputTempFile = Files.createTempFile("input", "." + format).toFile();
      try {
        Files.write(inputTempFile.toPath(), inputBytes);
        final File outputTempFile = adjustment.apply(inputTempFile);
        try {
          final byte[] outputBytes = Files.readAllBytes(outputTempFile.toPath());
          final String outputName = UUID.randomUUID() + ".png";
          toolContext
              .saveArtifact(outputName, Part.fromBytes(outputBytes, "image/png"))
              .blockingAwait();
          return ToolOutput.direct(Map.of("artifactName", outputName));
        } finally {
          outputTempFile.delete();
        }
      } finally {
        inputTempFile.delete();
      }
    } catch (Exception e) {
      LOG.error("{} failed for artifact={}", descriptor().name(), artifactName, e);
      return ToolOutput.direct(
          Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }
  }
}
