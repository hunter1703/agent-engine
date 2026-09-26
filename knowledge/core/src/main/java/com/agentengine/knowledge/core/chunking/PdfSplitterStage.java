package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.StringUtils;
import io.reactivex.rxjava3.core.Flowable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * The first stage of a PDF's chunking pipeline: walks the document in true document order — text
 * and embedded images interleaved exactly as they're drawn, not extracted as two separate passes
 * and concatenated — emitting a contiguous text chunk for each run of text and a still-undescribed
 * image chunk (see {@link ChunkUtils#isMedia}) for each embedded image, in that same order. A later
 * {@code ImageDescriptionStage} turns each image chunk into real text; whatever splitting or
 * merging stage runs in between leaves an image chunk whole (see {@link ChunkUtils#isMedia}) rather
 * than treating its absent text as content to divide or fold in.
 *
 * <p>Text comes from PDFBox reading the PDF's own internal structure directly rather than a
 * visual/OCR pass, so a page that's actually a scanned/photographed image embedded whole (no real
 * text layer at all) extracts no text of its own — the image chunk is what still gives such a page
 * any indexed content. A page rendered some other way with no discrete image resource (e.g. an
 * inline image operator) isn't covered by this — it would still extract as empty.
 *
 * <p>Usable as a pipeline's first stage, reading the whole document's raw bytes directly (see
 * {@link #apply(InputStream)}), or as a middle stage processing a chunk that itself carries an
 * embedded PDF's bytes (see {@link #apply(Flowable)}) — either way each PDF is parsed from its own
 * original bytes, never from already-decoded {@link KnowledgeChunk} text.
 */
public final class PdfSplitterStage extends ChunkingStage implements StreamingChunkingStage {

  private static final String IMAGE_FORMAT = "png";
  private static final String IMAGE_MIME_TYPE = "image/" + IMAGE_FORMAT;
  private static final String PDF_MIME_TYPE = "application/pdf";

  /**
   * Matches C0 control characters other than tab/newline/carriage-return. When a PDF's font has an
   * incomplete or non-standard {@code ToUnicode} CMap — common for complex scripts (e.g. Gujarati)
   * with conjunct/ligature glyphs — PDFBox can't map every glyph code to a real Unicode code point
   * and emits {@code U+0000} in its place rather than failing. Stripping these doesn't recover the
   * lost glyph (that data is genuinely gone at extraction), but keeps a NUL byte that some Mongo/
   * Qdrant client paths handle poorly out of persisted chunk text.
   */
  private static final Pattern NON_PRINTABLE_CONTROL_CHARS =
      Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

  @Override
  public Flowable<KnowledgeChunk> apply(final InputStream content) {
    return Flowable.fromIterable(toChunks(content));
  }

  /**
   * Runs as a middle stage too: a chunk carrying a whole embedded PDF's bytes (mime type {@link
   * #PDF_MIME_TYPE} — e.g. a PDF found within another container format) is replaced by that PDF's
   * own interleaved text/image chunks, processed the same way as the top-level document; every
   * other chunk passes through unchanged.
   */
  @Override
  protected Flowable<KnowledgeChunk> apply(final Flowable<KnowledgeChunk> chunks) {
    return chunks.concatMap(
        chunk ->
            PDF_MIME_TYPE.equals(chunk.getMimeType())
                ? Flowable.fromIterable(toChunks(new ByteArrayInputStream(chunk.getBytes())))
                : Flowable.just(chunk));
  }

  /**
   * Reads the whole document up front — PDFBox needs full structural access to a PDF regardless, so
   * there's no streaming variant of this to preserve — into an ordered list of chunks, each stamped
   * with its running position in the document (see {@link KnowledgeChunk#getChunkStart()}).
   */
  private static List<KnowledgeChunk> toChunks(final InputStream content) {
    try (PDDocument document = PDDocument.load(content)) {
      final PDFStripper extractor = new PDFStripper();
      extractor.getText(document);
      extractor.flushText();
      return extractor.chunks;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static KnowledgeChunk toTextChunk(final String text, final int offset) {
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setText(NON_PRINTABLE_CONTROL_CHARS.matcher(text).replaceAll(""));
    chunk.setChunkStart(offset);
    chunk.setChunkEnd(offset + text.length());
    return chunk;
  }

  private static KnowledgeChunk toImageChunk(final byte[] imageBytes, final int offset) {
    final KnowledgeChunk chunk = new KnowledgeChunk();
    chunk.setBytes(imageBytes);
    chunk.setMimeType(IMAGE_MIME_TYPE);
    chunk.setChunkStart(offset);
    chunk.setChunkEnd(offset);
    return chunk;
  }

  private static final class PDFStripper extends PDFTextStripper {
    private final List<KnowledgeChunk> chunks = new ArrayList<>();
    private final StringBuilder pendingText = new StringBuilder();
    private int offset;

    private PDFStripper() throws IOException {
      super();
    }

    @Override
    protected void writeString(final String text, final List<TextPosition> textPositions) {
      pendingText.append(text).append('\n');
    }

    @Override
    protected void processOperator(final Operator operator, final List<COSBase> operands)
        throws IOException {
      if ("Do".equals(operator.getName())
          && !operands.isEmpty()
          && operands.getFirst() instanceof COSName name) {
        final PDXObject xObject = getResources().getXObject(name);
        if (xObject instanceof PDImageXObject image) {
          flushText();
          chunks.add(toImageChunk(toPngBytes(image), offset));
        }
      }
      super.processOperator(operator, operands);
    }

    private void flushText() {
      final String text = pendingText.toString();
      if (StringUtils.isNotBlank(text)) {
        chunks.add(toTextChunk(text, offset));
        offset += text.length();
      }
      pendingText.setLength(0);
    }
  }

  private static byte[] toPngBytes(final PDImageXObject image) {
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image.getImage(), IMAGE_FORMAT, out);
      return out.toByteArray();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
