package com.agentengine.util.agents.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * An agent that tells which tags of the indexed knowledge an image belongs with. {@code modelId}
 * names the embedding model the image is compared by; only knowledge indexed with that model
 * counts.
 */
@JsonTypeName("IMAGE_CLASSIFICATION")
@BsonDiscriminator("com.agentengine.util.agents.beans.config.ImageClassificationAgentConfig")
public class ImageClassificationAgentConfig extends BaseAgentConfig {

  /** How many of a tag's closest indexed images its similarity score is the mean of. */
  private int closestChunksPerTag = 3;

  /** The similarity an indexed image needs to the input to count towards a tag. */
  private double matchThreshold = 0.4;

  /** How fast the probability of a match rises with the similarity above the threshold. */
  private double sharpness = 10.0;

  public ImageClassificationAgentConfig() {
    super(AgentType.IMAGE_CLASSIFICATION);
  }

  public int getClosestChunksPerTag() {
    return closestChunksPerTag;
  }

  public void setClosestChunksPerTag(final int closestChunksPerTag) {
    this.closestChunksPerTag = Math.max(1, closestChunksPerTag);
  }

  public double getMatchThreshold() {
    return matchThreshold;
  }

  public void setMatchThreshold(final double matchThreshold) {
    this.matchThreshold = matchThreshold;
  }

  public double getSharpness() {
    return sharpness;
  }

  public void setSharpness(final double sharpness) {
    this.sharpness = sharpness;
  }
}
