package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.cloudstorage.FileService;
import io.reactivex.rxjava3.core.Flowable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SingleChunkStage extends RootIndexingStage {

  public SingleChunkStage(FileService fileService, Knowledge knowledge) {
    super(fileService, knowledge);
  }

  @Override
  protected Flowable<KnowledgeChunk> apply() {
    return Flowable.fromSupplier(
        () -> {
          try (final InputStream inputStream = readFile()) {
            final KnowledgeChunk chunk = new KnowledgeChunk();
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            chunk.setText(text);
            chunk.setChunkStart(0);
            chunk.setChunkEnd(text.length());
            return chunk;
          }
        });
  }
}
