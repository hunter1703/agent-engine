package com.agentengine.util.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP helpers. They reach public hosts only, following each redirect themselves so no hop can lead
 * to an internal address. {@link #fetchAsText} reads at most {@link #MAX_BYTES} bytes of text
 * content.
 */
public final class HttpUtils {

  public static final int MAX_BYTES = 5_000_000;

  private static final int MAX_REDIRECTS = 5;
  private static final Set<String> TEXT_TYPES =
      Set.of("application/json", "application/xml", "application/xhtml+xml");
  private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  private HttpUtils() {}

  /** {@code url} with an {@code https://} scheme when it names none. */
  public static String withScheme(final String url) {
    final String trimmed = url.trim();
    return trimmed.regionMatches(true, 0, "http://", 0, 7)
            || trimmed.regionMatches(true, 0, "https://", 0, 8)
        ? trimmed
        : "https://" + trimmed;
  }

  /**
   * The page at {@code url} as text: HTML as Markdown, other text as it is.
   *
   * @throws IOException when the page cannot be fetched, is not text, is too large, or is not at a
   *     public address
   */
  public static String fetchAsText(final String url) throws IOException {
    final HttpResponse<InputStream> response = send("GET", url, HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream body = response.body()) {
      requireSuccess(response);
      return read(body, response.headers().firstValue("Content-Type").orElse("text/html"));
    }
  }

  /**
   * The content at {@code url}, which the caller closes.
   *
   * @throws IOException when it cannot be fetched or is not at a public address
   */
  public static InputStream open(final String url) throws IOException {
    final HttpResponse<InputStream> response = send("GET", url, HttpResponse.BodyHandlers.ofInputStream());
    try {
      requireSuccess(response);
    } catch (final IOException failed) {
      response.body().close();
      throw failed;
    }
    return response.body();
  }

  /**
   * The status and headers {@code url} answers a HEAD request with.
   *
   * @throws IOException when it cannot be reached or is not at a public address
   */
  public static HttpResponse<Void> head(final String url) throws IOException {
    return send("HEAD", url, HttpResponse.BodyHandlers.discarding());
  }

  /**
   * Sends the request, following each redirect itself so that no hop can lead to an internal
   * address.
   */
  private static <T> HttpResponse<T> send(
      final String method, final String url, final HttpResponse.BodyHandler<T> bodyHandler)
      throws IOException {
    URI uri = parse(url);
    try {
      for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
        requirePublic(uri);
        final HttpResponse<T> response =
            HTTP_CLIENT.send(
                HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build(),
                bodyHandler);
        final String location = response.headers().firstValue("Location").orElse(null);
        if (response.statusCode() / 100 != 3 || location == null) {
          return response;
        }
        if (response.body() instanceof InputStream stream) {
          stream.close();
        }
        uri = uri.resolve(location);
      }
    } catch (final InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while fetching " + url, interrupted);
    }
    throw new IOException("Too many redirects");
  }

  private static void requireSuccess(final HttpResponse<?> response) throws IOException {
    if (response.statusCode() >= 400) {
      throw new IOException("HTTP " + response.statusCode());
    }
  }

  private static String read(final InputStream body, final String contentType) throws IOException {
    final String mediaType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    if (!mediaType.startsWith("text/") && !TEXT_TYPES.contains(mediaType)) {
      throw new IOException("Unsupported content type: " + mediaType);
    }
    final byte[] bytes = body.readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      throw new IOException("The page is larger than " + MAX_BYTES / 1_000_000 + " MB");
    }
    final String text = new String(bytes, charsetOf(contentType));
    return mediaType.contains("html") ? MarkdownUtils.fromHtml(text) : text;
  }

  private static Charset charsetOf(final String contentType) {
    final Matcher matcher = CHARSET.matcher(contentType);
    try {
      return matcher.find() ? Charset.forName(matcher.group(1)) : StandardCharsets.UTF_8;
    } catch (final IllegalArgumentException unknown) {
      return StandardCharsets.UTF_8;
    }
  }

  private static URI parse(final String url) throws IOException {
    try {
      return URI.create(url);
    } catch (final IllegalArgumentException invalid) {
      throw new IOException("Not a valid URL: " + url);
    }
  }

  private static void requirePublic(final URI uri) throws IOException {
    final String scheme = uri.getScheme();
    if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
      throw new IOException("Only http and https URLs can be fetched: " + uri);
    }
    for (final InetAddress address : InetAddress.getAllByName(uri.getHost())) {
      if (!isPublic(address)) {
        throw new IOException("Not a public address: " + uri.getHost());
      }
    }
  }

  private static boolean isPublic(final InetAddress address) {
    final byte[] bytes = address.getAddress();
    final boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    final boolean sharedAddressSpace =
        bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64;
    return !(address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || uniqueLocal
        || sharedAddressSpace);
  }
}
