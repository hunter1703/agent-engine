package com.agentengine.runtime.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrepFilesToolTest {

  private final GrepFilesTool tool = new GrepFilesTool();

  @TempDir
  Path tempDir;

  @Test
  void shouldMatchFilesUsingRelativePathGlob() throws IOException {
    writeFile(tempDir.resolve("src/main/java/com/example/App.java"), """
        package com.example;

        class App {
          String value = "needle";
        }
        """);
    writeFile(tempDir.resolve("src/test/java/com/example/AppTest.java"), """
        package com.example;

        class AppTest {
          String value = "needle";
        }
        """);
    writeFile(tempDir.resolve("src/main/resources/application.properties"), "needle=true\n");

    final Map<String, Object> response = tool.execute("needle", tempDir.toString(), "src/main/**/*.java", 10, true);

    assertThat(intValue(response, "files_searched")).isEqualTo(1);
    assertThat(intValue(response, "total_matches")).isEqualTo(1);
    assertThat(matches(response)).singleElement().containsEntry("file", "src/main/java/com/example/App.java").containsEntry("line_number",
        4);
  }

  @Test
  void shouldSkipHiddenDirectoriesBeforeSearching() throws IOException {
    writeFile(tempDir.resolve(".git/objects/blob.txt"), "needle\n");
    writeFile(tempDir.resolve("_cache/search.txt"), "needle\n");
    writeFile(tempDir.resolve("visible/src/App.java"), "needle\n");

    final Map<String, Object> response = tool.execute("needle", tempDir.toString(), null, 10, true);

    assertThat(intValue(response, "files_searched")).isEqualTo(1);
    assertThat(intValue(response, "total_matches")).isEqualTo(1);
    assertThat(matches(response)).singleElement().containsEntry("file", "visible/src/App.java");
  }

  @Test
  void shouldReportTruncationWhenAdditionalMatchesExist() throws IOException {
    writeFile(tempDir.resolve("notes.txt"), """
        needle
        needle
        needle
        """);

    final Map<String, Object> response = tool.execute("needle", tempDir.toString(), "*.txt", 2, true);

    assertThat(intValue(response, "files_searched")).isEqualTo(1);
    assertThat(intValue(response, "matches_returned")).isEqualTo(2);
    assertThat(intValue(response, "total_matches")).isEqualTo(3);
    assertThat(response).containsEntry("truncated", true).containsEntry("limit", 2);
    assertThat(matches(response)).extracting(match -> ((Number) match.get("line_number")).intValue()).containsExactly(1, 2);
  }

  private void writeFile(final Path path, final String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> matches(final Map<String, Object> response) {
    return (List<Map<String, Object>>) response.get("matches");
  }

  private int intValue(final Map<String, Object> response, final String key) {
    return ((Number) response.get(key)).intValue();
  }
}
