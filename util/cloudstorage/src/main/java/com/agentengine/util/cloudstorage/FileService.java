package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.HttpUtils;
import com.agentengine.util.common.utils.StringUtils;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
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

  public FileDetails download(final FileDetails fileDetails) {
    InputStream content = getContent(fileDetails);
    return new FileDetails(
        fileDetails.name(),
        fileDetails.source(),
        fileDetails.type(),
        fileDetails.mimeType(),
        fileDetails.size(),
        content);
  }

  /**
   * The content of {@code fileDetails}, which the caller closes.
   *
   * @throws IllegalStateException if the content cannot be read
   */
  public InputStream getContent(final FileDetails fileDetails) {
    if (fileDetails == null) {
      return new ByteArrayInputStream(new byte[0]);
    }
    if (fileDetails.downloaded() != null) {
      return fileDetails.downloaded();
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
    } catch (final Exception ex) {
      throw new IllegalStateException(
          "Failed to read content of file " + JsonUtils.toJson(fileDetails), ex);
    }
  }

  public long getSize(final FileDetails fileDetails) {
    if (fileDetails == null) {
      return 0L;
    }
    if (fileDetails.size() > 0) {
      return fileDetails.size();
    }
    final String source = fileDetails.source();
    try {
      return switch (fileDetails.type()) {
        case CLOUDSTORAGE -> source != null ? cloudStorageService.getSize(source) : 0L;
        case LOCAL -> source != null ? Path.of(source).toFile().length() : 0L;
        case URL -> source != null ? fetchUrlSize(source) : 0L;
        default -> 0L;
      };
    } catch (Exception ex) {
      LOGGER.warn("Failed to determine size of file : {}", JsonUtils.toJson(fileDetails), ex);
      return 0L;
    }
  }

  /**
   * {@code fileDetails}, with the MIME type of a URL whose type is not known filled in: from the
   * extension of the URL's path, else from the {@code Content-Type} of the answer to a HEAD
   * request, which does not download the content. A type that cannot be told stays unknown.
   */
  public FileDetails withDetectedMimeType(final FileDetails fileDetails) {
    if (fileDetails == null
        || fileDetails.type() != FileDetails.StorageType.URL
        || fileDetails.source() == null
        || !isUnknownMimeType(fileDetails.mimeType())) {
      return fileDetails;
    }
    final String detected = detectUrlMimeType(fileDetails.source());
    return detected == null
        ? fileDetails
        : new FileDetails(
            fileDetails.name(),
            fileDetails.source(),
            fileDetails.type(),
            detected,
            fileDetails.size(),
            fileDetails.downloaded());
  }

  private static boolean isUnknownMimeType(final String mimeType) {
    return StringUtils.isBlank(mimeType)
        || FileDetails.UNKNOWN_MIME_TYPE.equalsIgnoreCase(mimeType);
  }

  private static InputStream fetchUrl(final String url) {
    try {
      return HttpUtils.open(url);
    } catch (final IOException ex) {
      throw new RuntimeException("Failed to fetch URL: " + url, ex);
    }
  }

  private static long fetchUrlSize(final String url) {
    try {
      final HttpResponse<Void> response = HttpUtils.head(url);
      return response.headers().firstValueAsLong("Content-Length").orElse(0L);
    } catch (Exception ex) {
      return 0L;
    }
  }

  private static String detectUrlMimeType(final String url) {
    try {
      final String path = URI.create(url).getPath();
      final String fromName = path == null ? null : URLConnection.guessContentTypeFromName(path);
      if (fromName != null) {
        return fromName;
      }
      final HttpResponse<Void> response = HttpUtils.head(url);
      if (response.statusCode() / 100 != 2) {
        return null;
      }
      return response
          .headers()
          .firstValue("Content-Type")
          .map(contentType -> contentType.split(";", 2)[0].trim().toLowerCase())
          .filter(StringUtils::isNotBlank)
          .orElse(null);
    } catch (final Exception ex) {
      return null;
    }
  }
}
