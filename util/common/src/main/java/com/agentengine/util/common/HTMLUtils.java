package com.agentengine.util.common;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Utility class for HTML processing operations.
 */
public final class HTMLUtils {
    private static final FlexmarkHtmlConverter CONVERTER;

    static {
        final MutableDataSet options = new MutableDataSet();
        // Configure options for better conversion quality
        options.set(FlexmarkHtmlConverter.BR_AS_EXTRA_BLANK_LINES, false);
        options.set(FlexmarkHtmlConverter.SKIP_HEADING_1, false);
        options.set(FlexmarkHtmlConverter.SKIP_HEADING_2, false);
        options.set(FlexmarkHtmlConverter.SKIP_HEADING_3, false);
        options.set(FlexmarkHtmlConverter.SKIP_HEADING_4, false);
        options.set(FlexmarkHtmlConverter.SKIP_HEADING_5, false);
        options.set(FlexmarkHtmlConverter.SKIP_HEADING_6, false);
        options.set(FlexmarkHtmlConverter.SKIP_FENCED_CODE, false);
        options.set(FlexmarkHtmlConverter.SKIP_CHAR_ESCAPE, false);

        CONVERTER = FlexmarkHtmlConverter.builder(options).build();
    }

    private HTMLUtils() {}

    /**
     * Converts HTML content to Markdown format.
     *
     * @param html the HTML content to convert
     * @return the converted Markdown content
     * @throws IllegalArgumentException if html is null
     */
    public static String htmlToMarkdown(final String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }

        return CONVERTER.convert(html);
    }
}
