package com.agentengine.util.common;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class JsonUtilsTest {
  @Test
  public void testPrimitivePolymorphic() throws Exception {
    String json = JsonUtils.toJson(12345L, true);
    String strJson = JsonUtils.toJson("hello", true);
    Files.writeString(
        Paths.get("test_results.txt"), "Long: " + json + "\nString: " + strJson + "\n");
  }
}
