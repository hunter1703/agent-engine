package com.agentengine.knowledge.api.services;

import com.agentengine.knowledge.api.beans.MediaDescription;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.ms.client.MicroService;

/** Turns media bytes (images, audio, video) into searchable text and descriptions. */
@MicroService("knowledge")
public interface MediaService {

  /** Describes media, transcribing any speech or visible text. */
  MediaDescription describe(FileDetails fileDetails);
}
