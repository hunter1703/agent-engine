package com.agentengine.engine.context;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LastNContextManager extends BaseContextManager {
  private static final Logger LOG = LoggerFactory.getLogger(LastNContextManager.class);

  public LastNContextManager(final int keepLast) {
    super(
        contents -> {
          LOG.debug(
              "Processing context with {} total contents, keeping last {} entries",
              contents.size(),
              keepLast);

          final List<Content> recent = new ArrayList<>();

          int remaining = keepLast * 3;
          boolean wasTrimmed = false;
          for (final Content content : contents.reversed()) {
            if (remaining == 0) {
                wasTrimmed = true;
              break;
            }
            final String text = content.text();
            LOG.debug("Processing content: text='{}', remaining={}", text, remaining);

            if (text == null) {
              continue;
            }
            final int actualLength = text.length();
            final int length = Math.min(remaining, actualLength);
            remaining -= length;
            if (length > 0) {
                if (length < actualLength) {
                    final String trimmed = text.substring(actualLength - length, actualLength);
                    final List<Part> nonTextPart = content.parts().orElse(new ArrayList<>()).stream().filter(part -> StringUtils.isNotBlank(part.text().orElse(null))).collect(Collectors.toCollection(ArrayList::new));
                    recent.add(content.toBuilder().parts(CollectionUtils.append(nonTextPart, Part.fromText(trimmed))).build());
                    LOG.debug("Added trimmed content: '{}'", trimmed);
                } else {
                    recent.add(content.toBuilder().build());
                }
            }
          }
          if (wasTrimmed) {
              recent.add(
                      Content.builder().role("user")
                              .parts(Part.builder().text("Following is the trimmed conversation").build())
                              .build());
              LOG.debug("Reached limit, adding trim indicator");
          }
          LOG.debug("Returning {} recent contents", recent.size());
          if (LOG.isDebugEnabled()) {
            for (int i = 0; i < recent.size(); i++) {
              Content c = recent.get(i);
              LOG.debug("Final context content[{}] - text='{}'", i, c.text());
            }
          }
          return recent.reversed();
        });
  }
}
