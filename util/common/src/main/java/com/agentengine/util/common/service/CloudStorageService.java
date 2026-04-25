package com.agentengine.util.common.service;

import com.agentengine.util.common.beans.FileDetails;
import java.io.InputStream;
import java.time.Duration;

public interface CloudStorageService {
    FileDetails upload(String name, InputStream inputStream, long contentLength, String mediaType);

    InputStream download(FileDetails fileDetails);

    InputStream downloadFromSource(String source);

    void delete(FileDetails fileDetails);

    String presignedGetUrl(FileDetails fileDetails, Duration validity);
}
