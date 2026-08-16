package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FileService {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);

  private final CloudStorageService cloudStorageService;

  public FileService(final CloudStorageService cloudStorageService) {
    this.cloudStorageService = cloudStorageService;
  }

  public InputStream getContent(final FileDetails fileDetails) {
    if (fileDetails == null) {
      return new ByteArrayInputStream(new byte[0]);
    }
    final String source = fileDetails.source();
    try {
      return switch (fileDetails.type()) {
        case URL -> fetchUrl(source);
        case LOCAL -> new FileInputStream(Path.of(source).toFile());
        case CLOUDSTORAGE -> cloudStorageService.download(fileDetails).stream();
        default ->
            throw new IllegalArgumentException("Unsupported storage type: " + fileDetails.type());
      };
    } catch (Exception ex) {
      LOGGER.error(
          "Encountered error while reading content of file : {}", JsonUtils.toJson(fileDetails));
      return null;
    }
  }

  // TODO: optimize
  private static InputStream fetchUrl(final String url) {
    try (final HttpClient http = HttpClient.newHttpClient()) {
      return http.send(
              HttpRequest.newBuilder(URI.create(url)).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream())
          .body();
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException("Failed to fetch URL: " + url, ex);
    }
  }
}
