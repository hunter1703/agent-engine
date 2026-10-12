package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.StringUtils;
import io.reactivex.rxjava3.core.Flowable;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * image chunk (see {@link ChunkUtils#isMedia}) for each embedded image, in that same order.
 */
public final class PdfChunkingStage extends RootIndexingStage {

  private static final String IMAGE_FORMAT = "jpeg";
  private static final String IMAGE_MIME_TYPE = "image/" + IMAGE_FORMAT;

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

  private final CloudStorageService cloudStorageService;

  public PdfChunkingStage(
      FileService fileService, Knowledge knowledge, CloudStorageService cloudStorageService) {
    super(fileService, knowledge);
    this.cloudStorageService = cloudStorageService;
  }

  @Override
  protected Flowable<KnowledgeChunk> apply() {
    return Flowable.using(
        this::readFile, content -> Flowable.fromIterable(toChunks(content)), InputStream::close);
  }

  /**
   * Reads the whole document up front — PDFBox needs full structural access to a PDF regardless, so
   * there's no streaming variant of this to preserve — into an ordered list of chunks, each stamped
   * with its running position in the document (see {@link KnowledgeChunk#getChunkStart()}).
   */
  private List<KnowledgeChunk> toChunks(final InputStream content) {
    try (PDDocument document = PDDocument.load(content)) {
      final PDFStripper extractor = new PDFStripper();
      extractor.getText(document);
      extractor.flushText();
      return extractor.chunks;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code image} as JPEG, flattened onto white since JPEG has no alpha channel. */
  private static byte[] toJpeg(final BufferedImage image) {
    final BufferedImage flattened =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = flattened.createGraphics();
    try {
      graphics.drawImage(image, 0, 0, Color.WHITE, null);
    } finally {
      graphics.dispose();
    }
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(flattened, IMAGE_FORMAT, out)) {
        throw new IllegalStateException("No ImageIO writer for " + IMAGE_FORMAT);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private final class PDFStripper extends PDFTextStripper {
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
          final byte[] jpeg = toJpeg(image.getImage());
          final FileDetails uploaded =
              cloudStorageService.upload(
                  UUID.randomUUID().toString(),
                  new ByteArrayInputStream(jpeg),
                  (long) jpeg.length,
                  IMAGE_MIME_TYPE);
          final KnowledgeChunk chunk = new KnowledgeChunk();
          chunk.setMedia(uploaded);
          chunk.setChunkStart(offset);
          chunk.setChunkEnd(offset + 1);
          chunks.add(chunk);
          offset += 1;
        }
      }
      super.processOperator(operator, operands);
    }

    private void flushText() {
      final String text = pendingText.toString();
      final String cleaned = NON_PRINTABLE_CONTROL_CHARS.matcher(text).replaceAll("");
      if (StringUtils.isNotBlank(cleaned)) {
        final KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setText(cleaned);
        chunk.setChunkStart(offset);
        chunk.setChunkEnd(offset + cleaned.length());
        chunks.add(chunk);
        offset += cleaned.length();
      }
      pendingText.setLength(0);
    }
  }
}
