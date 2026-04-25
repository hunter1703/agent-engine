package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.StructuredConcurrencyUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Shared image processing utilities for all image tools.
 * Covers I/O infrastructure (tiling, format detection, dimension reading) and
 * pixel-level math for color grading and exposure adjustment.
 */
public final class ImageUtils {

    private static final int TILE_SIZE = 256;

    private ImageUtils() {
    }

    // -------------------------------------------------------------------------
    // Tiled processing engine
    // -------------------------------------------------------------------------

    /**
     * Process an image file tile-by-tile, applying {@code pixelOp} to each tile's pixel array in place.
     * <p>
     * The tiling, I/O, normalization, output assembly, and file writing are handled here.
     * Callers supply only the pixel-level operation — everything else is identical across tools.
     *
     * @param inputFile the source image (JPEG or PNG)
     * @param pixelOp   operation applied to each tile's {@code int[]} of TYPE_INT_RGB packed pixels, in place
     * @return a temp file containing the processed image in the same format as the input
     */
    public static File processTiled(final File inputFile, final Consumer<int[]> pixelOp) throws IOException {
        final String format = getFileFormat(inputFile.getName());

        final int[] dimensions = readDimensions(inputFile, format);
        final int width = dimensions[0];
        final int height = dimensions[1];

        final List<Rectangle> tiles = buildTiles(width, height);

        final BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final List<Callable<Void>> callables = new ArrayList<>();
        for (final Rectangle tile : tiles) {
            callables.add(() -> {
                // each virtual thread gets its own ImageReader — ImageReader is not thread-safe
                final ImageReader reader = getImageReader(format);
                try (final ImageInputStream stream = ImageIO.createImageInputStream(inputFile)) {
                    reader.setInput(stream, false, true);
                    final BufferedImage normalized = readAndNormalizeTile(reader, tile);

                    // Direct DataBufferInt access gives a raw int[] into the image's backing store with no
                    // per-pixel method call overhead. getRGB/setRGB route through ColorModel conversion on
                    // every call — safe for arbitrary image types but ~10x slower at 100MP scale. This cast
                    // is guaranteed safe because readAndNormalizeTile always produces TYPE_INT_RGB.
                    final int[] pixels = ((DataBufferInt) normalized.getRaster().getDataBuffer()).getData();
                    pixelOp.accept(pixels);

                    synchronized (outputImage) {
                        final Graphics2D g2d = outputImage.createGraphics();
                        g2d.drawImage(normalized, tile.x, tile.y, null);
                        g2d.dispose();
                    }
                    return null;
                } finally {
                    reader.dispose();
                }
            });
        }
        StructuredConcurrencyUtils.runConcurrently(callables);

        final File outputFile = Files.createTempFile("processed", "." + format).toFile();
        ImageIO.write(outputImage, format, outputFile);
        return outputFile;
    }

    private static BufferedImage readAndNormalizeTile(final ImageReader reader, final Rectangle tile) throws IOException {
        final ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(tile);
        final BufferedImage rawTile = reader.read(0, param);

        if (rawTile.getType() == BufferedImage.TYPE_INT_RGB) {
            return rawTile;
        }
        // ImageIO decoders return varying BufferedImage types depending on format and color profile —
        // a JPEG may come back as TYPE_3BYTE_BGR, a PNG with transparency as TYPE_4BYTE_ABGR, etc.
        // DataBufferInt only backs TYPE_INT_* images; casting on any other type throws ClassCastException.
        // Redrawing into a fresh TYPE_INT_RGB buffer normalizes the representation unconditionally.
        final BufferedImage normalized = new BufferedImage(tile.width, tile.height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2d = normalized.createGraphics();
        g2d.drawImage(rawTile, 0, 0, null);
        g2d.dispose();
        return normalized;
    }
    // -------------------------------------------------------------------------
    // I/O and tiling
    // -------------------------------------------------------------------------

    /**
     * Partition an image into non-overlapping rectangular tiles of {@value TILE_SIZE}×{@value TILE_SIZE} pixels,
     * clipping edge tiles to the image bounds.
     */
    private static List<Rectangle> buildTiles(final int width, final int height) {
        final List<Rectangle> tiles = new ArrayList<>();
        for (int y = 0; y < height; y += TILE_SIZE) {
            for (int x = 0; x < width; x += TILE_SIZE) {
                final int w = Math.min(TILE_SIZE, width - x);
                final int h = Math.min(TILE_SIZE, height - y);
                tiles.add(new Rectangle(x, y, w, h));
            }
        }
        return tiles;
    }

    /** Read image dimensions without loading pixel data. */
    public static int[] readDimensions(final File file, final String format) throws IOException {
        final ImageReader reader = getImageReader(format);
        try (final ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            reader.setInput(stream, true, true);
            return new int[]{reader.getWidth(0), reader.getHeight(0)};
        } finally {
            reader.dispose();
        }
    }

    /** Obtain an {@link ImageReader} for the given format suffix. */
    public static ImageReader getImageReader(final String format) throws IOException {
        final Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix(format);
        if (!readers.hasNext()) {
            throw new IOException("No ImageReader found for format: " + format);
        }
        return readers.next();
    }

    /**
     * Derive the canonical format string from a filename.
     * Accepts jpg/jpeg → {@code "jpeg"}, png → {@code "png"}; throws for anything else.
     */
    public static String getFileFormat(final String filename) throws IOException {
        final int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            throw new IOException("Cannot determine format: no file extension in '" + filename + "'");
        }
        final String ext = filename.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            default -> throw new IOException("Unsupported image format: " + ext);
        };
    }

    /**
     * Detect image format from the first bytes of a stream.
     * Recognises JPEG ({@code FF D8 FF}) and PNG ({@code 89 PNG}).
     */
    public static String detectFormatFromHeader(final byte[] header) throws IOException {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (header.length >= 4
                && (header[0] & 0xFF) == 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G') {
            return "png";
        }
        throw new IOException("Cannot determine image format from file header");
    }

    // -------------------------------------------------------------------------
    // Color grading — HSL-based hue-targeted adjustments
    // -------------------------------------------------------------------------

    /**
     * Build a cosine falloff lookup table for the given hue width.
     * <p>
     * The LUT has 181 entries (indices 0–180). Entry {@code i} holds:
     * <ul>
     *   <li>{@code cos((i / hueWidth) * (π / 2))} when {@code i <= hueWidth}</li>
     *   <li>{@code 0.0} when {@code i > hueWidth}</li>
     * </ul>
     *
     * @param hueWidth half-width of the targeted hue range; must be in (0.0, 180.0]
     * @return 181-element double array
     */
    public static double[] buildCosLut(final double hueWidth) {
        if (hueWidth <= 0.0) {
            throw new IllegalArgumentException("hueWidth must be > 0, got: " + hueWidth);
        }
        final double[] lut = new double[181];
        for (int i = 0; i <= 180; i++) {
            lut[i] = (i <= hueWidth)
                    ? Math.cos((i / hueWidth) * (Math.PI / 2.0))
                    : 0.0;
        }
        return lut;
    }

    /**
     * Apply all hue-targeted color adjustments to a pixel array in place.
     * <p>
     * For each pixel: unpack RGB → convert to HSL → apply each adjustment in list order
     * → convert back to RGB → repack.
     *
     * @param pixels      TYPE_INT_RGB packed ints, modified in place
     * @param adjustments list of targeted adjustments
     * @param cosLuts     pre-computed cosine LUTs, one per adjustment (same order)
     */
    public static void applyColorAdjustments(
            final int[] pixels,
            final List<ColorAdjustment> adjustments,
            final List<double[]> cosLuts) {

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];

            final int r = (pixel >> 16) & 0xFF;
            final int g = (pixel >> 8) & 0xFF;
            final int b = pixel & 0xFF;

            final double[] hsl = rgbToHsl(r, g, b);
            double h = hsl[0];
            double s = hsl[1];
            double l = hsl[2];

            for (int j = 0; j < adjustments.size(); j++) {
                final ColorAdjustment adj = adjustments.get(j);
                final double[] cosLut = cosLuts.get(j);

                double deltaH = Math.abs(h - adj.hueCenter());
                if (deltaH > 180.0) {
                    deltaH = 360.0 - deltaH;
                }

                final double w = cosLut[Math.min((int) Math.round(deltaH), 180)];

                h = ((h + adj.hueShift() * w) % 360.0 + 360.0) % 360.0;
                s = Math.clamp(s + adj.satDelta() * w * 0.01, 0.0, 1.0);
                l = Math.clamp(l + adj.lumDelta() * w * 0.01, 0.0, 1.0);
            }

            final int[] rgb = hslToRgb(h, s, l);
            pixels[i] = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        }
    }

    // -------------------------------------------------------------------------
    // Exposure — luminance and tone adjustments
    // -------------------------------------------------------------------------

    /**
     * Apply exposure and tone adjustments to a pixel array in place.
     * <p>
     * Operations are applied in this order: blacks → whites → brightness → contrast → shadows/highlights.
     * Clipping points are established first so subsequent shifts operate within the new tonal range.
     *
     * @param pixels TYPE_INT_RGB packed ints, modified in place
     * @param adj    exposure parameters; all values on a [-100, 100] scale, 0 = no change
     */
    public static void applyExposureAdjustments(final int[] pixels, final ExposureAdjustment adj) {
        // Pre-compute constants outside the pixel loop.
        final int brightnessOffset = (int) Math.round(adj.brightness() * 2.55);  // [-100,100] → [-255,255]
        final double contrastFactor = (100.0 + adj.contrast()) / 100.0;          // >1 = more contrast
        final int whitesShift = (int) Math.round(adj.whites() * 1.275);          // 100 → ~128
        final int blacksShift = (int) Math.round(adj.blacks() * 1.275);
        final double highlightsFactor = adj.highlights() * 1.28;                 // max ±128 additive
        final double shadowsFactor = adj.shadows() * 1.28;

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            if (blacksShift != 0) {
                r = clamp(r + blacksShift);
                g = clamp(g + blacksShift);
                b = clamp(b + blacksShift);
            }

            if (whitesShift != 0) {
                r = clamp(r + whitesShift);
                g = clamp(g + whitesShift);
                b = clamp(b + whitesShift);
            }

            if (brightnessOffset != 0) {
                r = clamp(r + brightnessOffset);
                g = clamp(g + brightnessOffset);
                b = clamp(b + brightnessOffset);
            }

            if (adj.contrast() != 0.0) {
                r = clamp((int) Math.round((r - 128) * contrastFactor + 128));
                g = clamp((int) Math.round((g - 128) * contrastFactor + 128));
                b = clamp((int) Math.round((b - 128) * contrastFactor + 128));
            }

            if (adj.highlights() != 0.0 || adj.shadows() != 0.0) {
                // BT.601 perceived luminance
                final double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;

                if (adj.highlights() != 0.0 && lum > 0.5) {
                    final double w = (lum - 0.5) * 2.0;  // 0 at lum=0.5, 1 at lum=1.0
                    final int delta = (int) Math.round(highlightsFactor * w);
                    r = clamp(r + delta);
                    g = clamp(g + delta);
                    b = clamp(b + delta);
                }

                if (adj.shadows() != 0.0 && lum < 0.5) {
                    final double w = (0.5 - lum) * 2.0;  // 0 at lum=0.5, 1 at lum=0.0
                    final int delta = (int) Math.round(shadowsFactor * w);
                    r = clamp(r + delta);
                    g = clamp(g + delta);
                    b = clamp(b + delta);
                }
            }

            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // -------------------------------------------------------------------------
    // HSL ↔ RGB conversion (package-private for use by future tools)
    // -------------------------------------------------------------------------

    /**
     * Convert RGB to HSL.
     *
     * @param r red [0, 255]
     * @param g green [0, 255]
     * @param b blue [0, 255]
     * @return [h, s, l] where h ∈ [0, 360), s and l ∈ [0, 1]
     */
    private static double[] rgbToHsl(final int r, final int g, final int b) {
        final double rN = r / 255.0;
        final double gN = g / 255.0;
        final double bN = b / 255.0;

        final double max = Math.max(rN, Math.max(gN, bN));
        final double min = Math.min(rN, Math.min(gN, bN));
        final double delta = max - min;

        final double l = (max + min) / 2.0;
        final double s = (delta == 0.0) ? 0.0 : delta / (1.0 - Math.abs(2.0 * l - 1.0));

        final double h;
        if (delta == 0.0) {
            h = 0.0;
        } else if (max == rN) {
            h = 60.0 * (((gN - bN) / delta) % 6.0);
        } else if (max == gN) {
            h = 60.0 * ((bN - rN) / delta + 2.0);
        } else {
            h = 60.0 * ((rN - gN) / delta + 4.0);
        }

        return new double[]{h < 0.0 ? h + 360.0 : h, s, l};
    }

    /**
     * Convert HSL to RGB.
     *
     * @param h hue [0, 360)
     * @param s saturation [0, 1]
     * @param l lightness [0, 1]
     * @return [r, g, b] each in [0, 255]
     */
    private static int[] hslToRgb(final double h, final double s, final double l) {
        final double c = (1.0 - Math.abs(2.0 * l - 1.0)) * s;
        final double x = c * (1.0 - Math.abs((h / 60.0) % 2.0 - 1.0));
        final double m = l - c / 2.0;

        final double r1, g1, b1;
        switch ((int) (h / 60.0) % 6) {
            case 0 -> { r1 = c; g1 = x; b1 = 0; }
            case 1 -> { r1 = x; g1 = c; b1 = 0; }
            case 2 -> { r1 = 0; g1 = c; b1 = x; }
            case 3 -> { r1 = 0; g1 = x; b1 = c; }
            case 4 -> { r1 = x; g1 = 0; b1 = c; }
            default -> { r1 = c; g1 = 0; b1 = x; }
        }

        return new int[]{
            clamp((int) Math.round((r1 + m) * 255.0)),
            clamp((int) Math.round((g1 + m) * 255.0)),
            clamp((int) Math.round((b1 + m) * 255.0))
        };
    }

    private static int clamp(final int value) {
        return Math.clamp(value, 0, 255);
    }
}
