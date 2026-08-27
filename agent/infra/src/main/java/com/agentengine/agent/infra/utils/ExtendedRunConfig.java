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
  private final boolean newRun;

  public ExtendedRunConfig(
      final RunConfig delegate, final ResourceGrants grants, final boolean newRun) {
    this.delegate = delegate;
    this.grants = grants;
    this.newRun = newRun;
  }

  public ResourceGrants grants() {
    return grants;
  }

  /**
   * True for the {@code runAsync} call that starts a genuinely new run ; false for one that only
   * resumes an already-started run after a pause. Grants themselves must stay available on every
   * call — this only gates work that should happen once per run rather than once per resume, such
   * as recomputing knowledge/notebook reminders.
   */
  public boolean isNewRun() {
    return newRun;
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
            + "new ExtendedRunConfig(delegateConfig.toBuilder()...build(), grants(), isNewRun()) "
            + "instead.");
  }
}
