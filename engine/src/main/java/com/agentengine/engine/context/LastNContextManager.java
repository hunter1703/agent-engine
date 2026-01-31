package com.agentengine.engine.context;

import com.google.genai.types.Content;
import com.google.genai.types.Part;

import java.util.ArrayList;
import java.util.List;

public final class LastNContextManager extends BaseContextManager {

  public LastNContextManager(final int keepLast) {
    super(contents -> {
      final List<Content> recent = new ArrayList<>();

      int remaining = keepLast * 3;
      for (final Content content : contents.reversed()) {
        if (remaining == 0) {
          break;
        }
        final String text = content.text();
        if (text == null) {
          continue;
        }
        final int actualLength = text.length();
        final int length = Math.min(remaining, actualLength);
        remaining -= length;
        if (length > 0) {
          final String trimmed = text.substring(actualLength - length, actualLength);
          recent.add(Content.builder().parts(Part.builder().text(trimmed).build()).build());
        }
        if (remaining == 0) {
          recent.add(
              Content.builder().parts(Part.builder().text("Following is the trimmed conversation").build()).build());
          break;
        }
      }
      return recent.reversed();
    });
  }
}
