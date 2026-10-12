package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.cloudstorage.FileService;
import io.reactivex.rxjava3.core.Flowable;
import java.io.InputStream;

public abstract class RootIndexingStage extends IndexingStage {

  private final FileService fileService;
  private final Knowledge knowledge;

  protected RootIndexingStage(FileService fileService, Knowledge knowledge) {
    this.fileService = fileService;
    this.knowledge = knowledge;
  }

  protected Flowable<KnowledgeChunk> apply(Flowable<KnowledgeChunk> chunks) {
    return apply();
  }

  protected abstract Flowable<KnowledgeChunk> apply();

  protected Knowledge knowledge() {
    return knowledge;
  }

  protected InputStream readFile() {
    return fileService.getContent(knowledge.getFileDetails());
  }
}
