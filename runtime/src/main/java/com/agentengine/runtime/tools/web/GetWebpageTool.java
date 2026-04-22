package com.agentengine.runtime.tools.web;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolConstructor;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.HTMLUtils;
import com.agentengine.util.common.StringUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;


@DiscoverableTool
public final class GetWebpageTool extends Tool {
    private static final String TOOL_NAME = "get_webpage";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Fetches a web page from the provided URL and returns its content converted to Markdown format. "
                    + "Use this tool to retrieve documentation, articles, or any web content that needs to be "
                    + "processed or analyzed. The HTML content is automatically converted to clean Markdown for "
                    + "easier reading and processing. "
                    + "Returns: { markdown: \"<converted markdown content>\", url: \"<requested url>\", "
                    + "statusCode: <http status code> }.");

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AgentEngine/1.0; +https://github.com/agentengine)";
    private static final HttpClient CLIENT;

    static {
        CLIENT = HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @ToolConstructor
    public GetWebpageTool() {
        super(DESCRIPTOR);
    }

    /**
     * Fetches a web page and converts it to Markdown.
     *
     * @param url the URL of the web page to fetch
     * @return a map containing the markdown content, original URL, and HTTP status code
     */
    public Map<String, Object> execute(
            @ToolSchema(
                            name = "url",
                            description = "The URL of the web page to fetch. Must be a valid HTTP or HTTPS URL.")
                    String url) {
        if (StringUtils.isBlank(url)) {
            return Map.of("error", "URL cannot be null or empty", "statusCode", 400);
        }

        try {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            final URI uri = URI.create(url);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            final int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                final String html = response.body();
                final String markdown = HTMLUtils.htmlToMarkdown(html);

                return Map.of(
                        "markdown", markdown,
                        "url", url,
                        "statusCode", statusCode,
                        "contentLength", html.length());
            } else {
                return Map.of(
                        "error",
                        "HTTP request failed with status code: " + statusCode,
                        "url",
                        url,
                        "statusCode",
                        statusCode);
            }

        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid URL format: " + e.getMessage(), "url", url, "statusCode", 400);
        } catch (IOException e) {
            return Map.of("error", "Failed to fetch webpage: " + e.getMessage(), "url", url, "statusCode", 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("error", "Request was interrupted: " + e.getMessage(), "url", url, "statusCode", 500);
        } catch (Exception e) {
            return Map.of("error", "Unexpected error: " + e.getMessage(), "url", url, "statusCode", 500);
        }
    }
}
