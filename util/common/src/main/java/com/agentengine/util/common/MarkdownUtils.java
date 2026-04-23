package com.agentengine.util.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.*;

import org.commonmark.node.*;
import org.commonmark.renderer.markdown.MarkdownRenderer;

/**
 * Utility class for producing Markdown from arbitrary objects and HTML.
 *
 * <ul>
 *   <li>{@link #fromObject(Object)} — walks the Jackson node tree and builds a commonmark-java AST,
 *       then renders it to Markdown. Collections become bullet lists; objects become labeled
 *       sections with headings or bold labels; scalars become inline text.
 *   <li>{@link #fromHtml(String)} — converts HTML to Markdown via flexmark's
 *       {@link FlexmarkHtmlConverter}.
 * </ul>
 */
public final class MarkdownUtils {

    private static final int MAX_HEADING_LEVEL = 6;
    private static final MarkdownRenderer MARKDOWN_RENDERER = MarkdownRenderer.builder().build();

    private static final FlexmarkHtmlConverter HTML_CONVERTER;

    static {
        final MutableDataSet options = new MutableDataSet();
        options.set(FlexmarkHtmlConverter.BR_AS_EXTRA_BLANK_LINES, false);
        options.set(FlexmarkHtmlConverter.SKIP_FENCED_CODE, false);
        options.set(FlexmarkHtmlConverter.SKIP_CHAR_ESCAPE, false);
        HTML_CONVERTER = FlexmarkHtmlConverter.builder(options).build();
    }

    private MarkdownUtils() {}

    /**
     * Converts any object to a Markdown string.
     *
     * <p>The object is serialized to a Jackson {@link JsonNode} tree, then rendered:
     * <ul>
     *   <li>Objects: each field becomes a heading (shallow depth) or bold label (deep), with its
     *       value rendered below.
     *   <li>Arrays: each element becomes a bullet-list item.
     *   <li>Scalars: emitted as plain paragraph text.
     * </ul>
     *
     * @param object the object to render; {@code null} returns an empty string
     * @return Markdown string
     */
    public static String fromObject(final Object object) {
        if (object == null) {
            return "";
        }
        final JsonNode node = JsonUtils.toJsonNode(object);
        final Document document = new Document();
        nodesFor(node, 1, null).forEach(document::appendChild);
        return MARKDOWN_RENDERER.render(document).stripTrailing();
    }

    /**
     * Converts HTML to Markdown using flexmark's {@link FlexmarkHtmlConverter}.
     *
     * @param html the HTML string to convert; blank input returns an empty string
     * @return Markdown string
     */
    public static String fromHtml(final String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        return HTML_CONVERTER.convert(html);
    }

    // -------------------------------------------------------------------------
    // Internal AST construction — each method returns nodes, never mutates args
    // -------------------------------------------------------------------------

    /**
     * Returns the list of commonmark {@link Node}s that represent {@code jsonNode}.
     *
     * @param jsonNode the JSON value to render
     * @param depth    current heading depth (1 = h1)
     * @param label    field name from the enclosing object, or {@code null} for array items / root
     */
    private static List<Node> nodesFor(final JsonNode jsonNode, final int depth, final String label) {
        if (jsonNode.isObject()) {
            return objectNodes(jsonNode, depth, label);
        } else if (jsonNode.isArray()) {
            return arrayNodes(jsonNode, depth, label);
        } else {
            return scalarNodes(jsonNode, depth, label);
        }
    }

    private static List<Node> objectNodes(final JsonNode jsonNode, final int depth, final String label) {
        final List<Node> nodes = new ArrayList<>();
        if (label != null) {
            nodes.add(labelNode(label, depth));
        }
        final Set<Map.Entry<String, JsonNode>> fields = jsonNode.properties();
        for (final Map.Entry<String, JsonNode> entry : fields) {
            nodes.addAll(nodesFor(entry.getValue(), depth + 1, entry.getKey()));
        }
        return nodes;
    }

    private static List<Node> arrayNodes(final JsonNode jsonNode, final int depth, final String label) {
        final List<Node> nodes = new ArrayList<>();
        if (label != null) {
            nodes.add(labelNode(label, depth));
        }

        // Scalar arrays → bullet list; complex arrays → numbered heading sections
        if (allScalars(jsonNode)) {
            final BulletList list = new BulletList();
            for (final JsonNode element : jsonNode) {
                final ListItem item = new ListItem();
                final Paragraph paragraph = new Paragraph();
                paragraph.appendChild(new Text(element.isNull() ? "" : element.asText()));
                item.appendChild(paragraph);
                list.appendChild(item);
            }
            nodes.add(list);
        } else {
            int index = 1;
            for (final JsonNode element : jsonNode) {
                final String itemLabel = label != null ? formatLabel(label) + " " + index : "Item " + index;
                nodes.addAll(nodesFor(element, depth, itemLabel));
                index++;
            }
        }
        return nodes;
    }

    private static boolean allScalars(final JsonNode arrayNode) {
        for (final JsonNode element : arrayNode) {
            if (!element.isValueNode()) {
                return false;
            }
        }
        return true;
    }

    private static List<Node> scalarNodes(final JsonNode jsonNode, final int depth, final String label) {
        final String text = jsonNode.isNull() ? "" : jsonNode.asText();
        final List<Node> nodes = new ArrayList<>();

        if (label != null) {
            if (depth <= MAX_HEADING_LEVEL) {
                nodes.add(labelNode(label, depth));
            } else {
                final Paragraph paragraph = new Paragraph();
                final StrongEmphasis strong = new StrongEmphasis();
                strong.appendChild(new Text(formatLabel(label) + ": "));
                paragraph.appendChild(strong);
                if (!text.isEmpty()) {
                    paragraph.appendChild(new Text(text));
                }
                return List.of(paragraph);
            }
        }
        if (!text.isEmpty()) {
            final Paragraph paragraph = new Paragraph();
            paragraph.appendChild(new Text(text));
            nodes.add(paragraph);
        }
        return nodes;
    }

    /**
     * Returns a {@link Heading} node for depth ≤ {@value MAX_HEADING_LEVEL},
     * or a bold {@link Paragraph} beyond that.
     */
    private static Node labelNode(final String label, final int depth) {
        final String title = formatLabel(label);
        if (depth <= MAX_HEADING_LEVEL) {
            final Heading heading = new Heading();
            heading.setLevel(depth);
            heading.appendChild(new Text(title));
            return heading;
        }
        // Beyond h6: bold label + value in one paragraph
        final Paragraph paragraph = new Paragraph();
        final StrongEmphasis strong = new StrongEmphasis();
        strong.appendChild(new Text(title));
        paragraph.appendChild(strong);
        return paragraph;
    }

    /**
     * Converts a camelCase or snake_case field name to a human-readable title.
     * e.g. {@code "subViolations"} → {@code "Sub Violations"}, {@code "error_code"} → {@code "Error Code"}.
     */
    private static String formatLabel(final String name) {
        final String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
        final String[] words = spaced.split(" ");
        final StringBuilder sb = new StringBuilder();
        for (final String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
