package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.ResourceGrants;
import com.google.adk.agents.RunConfig;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Modality;
import com.google.genai.types.SpeechConfig;

public final class ExtendedRunConfig extends RunConfig {
  private final RunConfig delegate;
  private final ResourceGrants grants;

  public ExtendedRunConfig(final RunConfig delegate, final ResourceGrants grants) {
    this.delegate = delegate;
    this.grants = grants;
  }

  public ResourceGrants grants() {
    return grants;
  }

  @Override
  public SpeechConfig speechConfig() {
    return delegate.speechConfig();
  }

  @Override
  public ImmutableList<Modality> responseModalities() {
    return delegate.responseModalities();
  }

  @Override
  public boolean saveInputBlobsAsArtifacts() {
    return delegate.saveInputBlobsAsArtifacts();
  }

  @Override
  public StreamingMode streamingMode() {
    return delegate.streamingMode();
  }

  @Override
  public ToolExecutionMode toolExecutionMode() {
    return delegate.toolExecutionMode();
  }

  @Override
  public AudioTranscriptionConfig outputAudioTranscription() {
    return delegate.outputAudioTranscription();
  }

  @Override
  public AudioTranscriptionConfig inputAudioTranscription() {
    return delegate.inputAudioTranscription();
  }

  @Override
  public int maxLlmCalls() {
    return delegate.maxLlmCalls();
  }

  @Override
  public boolean autoCreateSession() {
    return delegate.autoCreateSession();
  }

  @Override
  public Builder toBuilder() {
    throw new UnsupportedOperationException(
        "ExtendedRunConfig.toBuilder().build() would silently lose its ResourceGrants — build a "
            + "new ExtendedRunConfig(delegateConfig.toBuilder()...build(), grants()) instead.");
  }
}
