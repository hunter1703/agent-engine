package com.agentengine.runtime.tools.image;

import com.agentengine.util.common.StructuredConcurrencyUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBufferInt;
import java.awt.image.Kernel;
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
 * pixel-level math for color grading, exposure, temperature, split toning, vignette, and clarity.
 */
public final class ImageUtils {

    private static final int TILE_SIZE = 256;
    private static final float JPEG_OUTPUT_QUALITY = 0.95f;

    private ImageUtils() {
    }

    // -------------------------------------------------------------------------
    // Tile operation functional interface
    // -------------------------------------------------------------------------

    /**
     * Pixel operation that receives both the pixel array and the tile's position within the full image.
     * Use this instead of {@link Consumer} when the operation needs to know absolute pixel coordinates
     * (e.g. vignette, gradient masks).
     */
    @FunctionalInterface
    public interface TileOperation {
        /**
         * @param pixels    TYPE_INT_RGB packed ints for this tile, modified in place
         * @param tileX     left edge of this tile in image coordinates
         * @param tileY     top edge of this tile in image coordinates
         * @param tileW     width of this tile in pixels
         * @param tileH     height of this tile in pixels
         * @param imageW    full image width
         * @param imageH    full image height
         */
        void apply(int[] pixels, int tileX, int tileY, int tileW, int tileH, int imageW, int imageH);
    }

    // -------------------------------------------------------------------------
    // Tiled processing engine
    // -------------------------------------------------------------------------

    /**
     * Process an image file tile-by-tile, applying {@code pixelOp} to each tile's pixel array in place.
     * Use this overload when the operation does not need tile coordinates.
     */
    public static File processTiledOnPixel(final File inputFile, final Consumer<int[]> pixelOp) {
        return processTiled(inputFile, (pixels, tx, ty, tw, th, iw, ih) -> pixelOp.accept(pixels));
    }

    /**
     * Process an image file tile-by-tile, applying {@code tileOp} to each tile with full coordinate context.
     * Use this overload when the operation needs to know each pixel's absolute position (e.g. vignette).
     */
    public static File processTiled(final File inputFile, final TileOperation tileOp) {
        try {
            final String format = getFileFormat(inputFile.getName());

            final int[] dimensions = readDimensions(inputFile, format);
            final int width = dimensions[0];
            final int height = dimensions[1];

            final List<Rectangle> tiles = buildTiles(width, height);

            final BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            final List<Callable<Void>> callables = new ArrayList<>();
            for (final Rectangle tile : tiles) {
                callables.add(() -> {
                    final ImageReader reader = getImageReader(format);
                    try (final ImageInputStream stream = ImageIO.createImageInputStream(inputFile)) {
                        reader.setInput(stream, false, true);
                        final BufferedImage normalized = readAndNormalizeTile(reader, tile);

                        final int[] pixels = ((DataBufferInt) normalized.getRaster().getDataBuffer()).getData();
                        tileOp.apply(pixels, tile.x, tile.y, tile.width, tile.height, width, height);

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
            try {
                writeImage(outputImage, format, outputFile);
            } catch (IOException e) {
                outputFile.delete();
                throw e;
            }
            return outputFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write a {@link BufferedImage} to a file at high quality.
     * JPEG is written at {@value JPEG_OUTPUT_QUALITY} quality to prevent generational loss on repeated edits.
     * PNG is lossless and written directly.
     */
    static void writeImage(final BufferedImage image, final String format, final File outputFile) throws IOException {
        if ("jpeg".equals(format)) {
            final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IOException("No JPEG ImageWriter available");
            }
            final ImageWriter writer = writers.next();
            try (final ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
                writer.setOutput(ios);
                final ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_OUTPUT_QUALITY);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            ImageIO.write(image, format, outputFile);
        }
    }

    private static BufferedImage readAndNormalizeTile(final ImageReader reader, final Rectangle tile) throws IOException {
        final ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(tile);
        final BufferedImage rawTile = reader.read(0, param);

        if (rawTile.getType() == BufferedImage.TYPE_INT_RGB) {
            return rawTile;
        }
        final BufferedImage normalized = new BufferedImage(tile.width, tile.height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2d = normalized.createGraphics();
        g2d.drawImage(rawTile, 0, 0, null);
        g2d.dispose();
        return normalized;
    }

    // -------------------------------------------------------------------------
    // I/O and tiling helpers
    // -------------------------------------------------------------------------

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
     * Entry {@code i} holds {@code cos((i / hueWidth) * (π/2))} for i ≤ hueWidth, else 0.
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
    // Exposure — four-zone tone curve
    // -------------------------------------------------------------------------

    /**
     * Apply exposure and tone adjustments to a pixel array in place.
     * <p>
     * The four zone parameters ({@code blacks}, {@code shadows}, {@code highlights}, {@code whites})
     * define vertical offsets at the midpoint of each luminance quarter. A Catmull-Rom spline is
     * interpolated through all six control points — the two fixed anchors at (0,0) and (1,1) plus
     * the four zone points — and sampled into a 256-entry LUT applied per channel.
     * <p>
     * Operation order: brightness offset → tone curve LUT → saturation.
     *
     * @param pixels TYPE_INT_RGB packed ints, modified in place
     * @param adj    exposure parameters; all values on a [-100, 100] scale, 0 = no change
     */
    public static void applyExposureAdjustments(final int[] pixels, final ExposureAdjustment adj) {
        final int brightnessOffset = (int) Math.round(adj.brightness() * 2.55);
        final int[] toneLut = buildToneCurveLut(adj);

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // 1. Brightness — global offset before the curve
            if (brightnessOffset != 0) {
                r = clamp(r + brightnessOffset);
                g = clamp(g + brightnessOffset);
                b = clamp(b + brightnessOffset);
            }

            // 2. Four-zone tone curve — single LUT lookup per channel
            r = toneLut[r];
            g = toneLut[g];
            b = toneLut[b];

            // 3. Global saturation
            if (adj.saturation() != 0.0) {
                final double[] hsl = rgbToHsl(r, g, b);
                hsl[1] = Math.clamp(hsl[1] + adj.saturation() * 0.01, 0.0, 1.0);
                final int[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
                r = rgb[0];
                g = rgb[1];
                b = rgb[2];
            }

            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Build a 256-entry tone curve LUT from the four zone offsets in {@code adj}.
     * <p>
     * Six control points are placed on the curve:
     * <pre>
     *   (0.000, 0.000)  — fixed black anchor
     *   (0.125, blacks_y)   — midpoint of the blacks zone  (0–25%)
     *   (0.375, shadows_y)  — midpoint of the shadows zone (25–50%)
     *   (0.625, highlights_y) — midpoint of the highlights zone (50–75%)
     *   (0.875, whites_y)   — midpoint of the whites zone  (75–100%)
     *   (1.000, 1.000)  — fixed white anchor
     * </pre>
     * Each zone y-value is {@code x + offset/100 * 0.5}, clamped to [0,1].
     * A Catmull-Rom spline is evaluated through these points and sampled into the LUT.
     * When all zone offsets are zero the curve is the identity (straight diagonal).
     */
    private static int[] buildToneCurveLut(final ExposureAdjustment adj) {
        // Scale: offset of ±100 shifts the zone midpoint by ±0.5 in [0,1] space
        final double scale = 0.5 / 100.0;
        final double[] px = {0.000, 0.125, 0.375, 0.625, 0.875, 1.000};
        final double[] py = {
            0.000,
            Math.clamp(0.125 + adj.blacks()      * scale, 0.0, 1.0),
            Math.clamp(0.375 + adj.shadows()     * scale, 0.0, 1.0),
            Math.clamp(0.625 + adj.highlights()  * scale, 0.0, 1.0),
            Math.clamp(0.875 + adj.whites()      * scale, 0.0, 1.0),
            1.000
        };

        final int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            final double x = i / 255.0;
            lut[i] = clamp((int) Math.round(catmullRom(px, py, x) * 255.0));
        }
        return lut;
    }

    /**
     * Evaluate a Catmull-Rom spline at position {@code x} given sorted knot arrays.
     * Uses phantom end-points (reflection of the first and last segments) so the curve
     * passes through all supplied points including the endpoints.
     */
    private static double catmullRom(final double[] kx, final double[] ky, final double x) {
        final int n = kx.length;

        // Find the segment containing x
        int seg = n - 2;
        for (int i = 0; i < n - 1; i++) {
            if (x <= kx[i + 1]) {
                seg = i;
                break;
            }
        }

        // Local parameter t ∈ [0,1] within the segment
        final double dx = kx[seg + 1] - kx[seg];
        final double t = (dx == 0.0) ? 0.0 : (x - kx[seg]) / dx;

        // Catmull-Rom requires four points: P0, P1, P2, P3
        // Use phantom points at the boundaries by reflecting the adjacent segment
        final double p0 = (seg > 0)     ? ky[seg - 1] : 2 * ky[0]     - ky[1];
        final double p1 = ky[seg];
        final double p2 = ky[seg + 1];
        final double p3 = (seg < n - 2) ? ky[seg + 2] : 2 * ky[n - 1] - ky[n - 2];

        // Standard Catmull-Rom formula
        final double t2 = t * t;
        final double t3 = t2 * t;
        final double y = 0.5 * ((2 * p1)
                + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        return Math.clamp(y, 0.0, 1.0);
    }

    // -------------------------------------------------------------------------
    // Temperature and tint
    // -------------------------------------------------------------------------

    /**
     * Apply white balance (temperature + tint) adjustments to a pixel array in place.
     * <p>
     * Temperature shifts the warm/cool axis by scaling R and B channels inversely.
     * Tint shifts the green/magenta axis by scaling G relative to R and B.
     * Coefficients are calibrated so ±100 approximates a full tungsten↔daylight shift.
     *
     * @param pixels TYPE_INT_RGB packed ints, modified in place
     * @param adj    temperature and tint parameters
     */
    public static void applyTemperatureAdjustment(final int[] pixels, final TemperatureAdjustment adj) {
        if (adj.temperature() == 0.0 && adj.tint() == 0.0) {
            return;
        }

        // Temperature: +100 → R*1.20, G*1.05, B*0.70 (tungsten warmth)
        //              -100 → R*0.80, G*0.95, B*1.30 (cool shade)
        final double tempT = adj.temperature() / 100.0;
        final double rTempScale = 1.0 + tempT * 0.20;
        final double gTempScale = 1.0 + tempT * 0.05;
        final double bTempScale = 1.0 - tempT * 0.30;

        // Tint: +100 → R*1.10, G*0.80, B*1.10 (magenta)
        //       -100 → R*0.90, G*1.20, B*0.90 (green)
        final double tintT = adj.tint() / 100.0;
        final double rTintScale = 1.0 + tintT * 0.10;
        final double gTintScale = 1.0 - tintT * 0.20;
        final double bTintScale = 1.0 + tintT * 0.10;

        final double rScale = rTempScale * rTintScale;
        final double gScale = gTempScale * gTintScale;
        final double bScale = bTempScale * bTintScale;

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];
            final int r = clamp((int) Math.round(((pixel >> 16) & 0xFF) * rScale));
            final int g = clamp((int) Math.round(((pixel >> 8) & 0xFF) * gScale));
            final int b = clamp((int) Math.round((pixel & 0xFF) * bScale));
            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // -------------------------------------------------------------------------
    // Split toning
    // -------------------------------------------------------------------------

    /**
     * Apply split toning to a pixel array in place.
     * <p>
     * Blends a shadow colour tint into dark pixels and a highlight colour tint into bright pixels,
     * with a smooth luminance-based crossover. Works in HSL space to preserve luminance.
     *
     * @param pixels TYPE_INT_RGB packed ints, modified in place
     * @param adj    split tone parameters
     */
    public static void applySplitTone(final int[] pixels, final SplitToneAdjustment adj) {
        if (adj.shadowSaturation() == 0.0 && adj.highlightSaturation() == 0.0) {
            return;
        }

        // Crossover midpoint shifts with balance: +100 → 0.75 (more highlights), -100 → 0.25 (more shadows)
        final double midpoint = 0.5 + adj.balance() * 0.0025;
        final double shadowStrength = adj.shadowSaturation() / 100.0;
        final double highlightStrength = adj.highlightSaturation() / 100.0;

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];
            final int r = (pixel >> 16) & 0xFF;
            final int g = (pixel >> 8) & 0xFF;
            final int b = pixel & 0xFF;

            final double[] hsl = rgbToHsl(r, g, b);
            final double lum = hsl[2];

            double blendH = hsl[0];
            double blendS = hsl[1];

            if (lum <= midpoint && shadowStrength > 0.0) {
                // Shadow region: weight rises from 0 at midpoint to 1 at black
                final double t = (midpoint - lum) / midpoint;
                final double w = shadowStrength * (1.0 - Math.cos(t * Math.PI / 2.0));
                // Blend hue toward shadow hue, introduce saturation on neutral pixels
                blendH = blendHue(blendH, adj.shadowHue(), w);
                blendS = Math.clamp(blendS + w * (1.0 - blendS) * 0.5, 0.0, 1.0);
            }

            if (lum > midpoint && highlightStrength > 0.0) {
                // Highlight region: weight rises from 0 at midpoint to 1 at white
                final double t = (lum - midpoint) / (1.0 - midpoint);
                final double w = highlightStrength * (1.0 - Math.cos(t * Math.PI / 2.0));
                blendH = blendHue(blendH, adj.highlightHue(), w);
                blendS = Math.clamp(blendS + w * (1.0 - blendS) * 0.5, 0.0, 1.0);
            }

            final int[] rgb = hslToRgb(blendH, blendS, lum);
            pixels[i] = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        }
    }

    /** Blend hue {@code from} toward hue {@code to} by weight {@code w}, respecting the circular hue axis. */
    private static double blendHue(final double from, final double to, final double w) {
        double delta = to - from;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return ((from + delta * w) % 360.0 + 360.0) % 360.0;
    }

    // -------------------------------------------------------------------------
    // Vignette
    // -------------------------------------------------------------------------

    /**
     * Apply a vignette effect to a tile in place.
     * <p>
     * Each pixel's distance from the image centre determines how much darkening or lightening is applied,
     * using a cosine falloff between the inner (clear) zone and the outer (fully vignetted) edge.
     *
     * @param pixels  TYPE_INT_RGB packed ints for this tile, modified in place
     * @param tileX   left edge of this tile in image coordinates
     * @param tileY   top edge of this tile in image coordinates
     * @param tileW   width of this tile
     * @param tileH   height of this tile
     * @param imageW  full image width
     * @param imageH  full image height
     * @param adj     vignette parameters
     */
    public static void applyVignette(
            final int[] pixels,
            final int tileX, final int tileY, final int tileW, final int tileH,
            final int imageW, final int imageH,
            final VignetteAdjustment adj) {

        if (adj.strength() == 0.0) {
            return;
        }

        final double cx = imageW / 2.0;
        final double cy = imageH / 2.0;
        // Normalise distances by the half-diagonal so corners are always at distance 1.0
        final double halfDiag = Math.sqrt(cx * cx + cy * cy);

        // Inner radius: fraction of half-diagonal where vignette starts (size=50 → 0.5)
        final double innerRadius = (adj.size() <= 0 ? 50.0 : adj.size()) / 100.0;
        // Feather zone width: fraction of half-diagonal over which the transition occurs
        final double featherWidth = Math.max(0.01, (adj.feather() <= 0 ? 50.0 : adj.feather()) / 100.0) * 0.5;
        final double outerRadius = innerRadius + featherWidth;

        // Brightness multiplier at full vignette: strength=-100 → 0.0 (black), strength=+100 → 2.0 (white)
        final double fullFactor = 1.0 + adj.strength() / 100.0;

        for (int py = 0; py < tileH; py++) {
            for (int px = 0; px < tileW; px++) {
                final double dx = (tileX + px - cx) / halfDiag;
                final double dy = (tileY + py - cy) / halfDiag;
                final double dist = Math.sqrt(dx * dx + dy * dy);

                final double factor;
                if (dist <= innerRadius) {
                    factor = 1.0;  // inside clear zone — no adjustment
                } else if (dist >= outerRadius) {
                    factor = fullFactor;  // outside feather zone — full vignette
                } else {
                    // Smooth cosine transition through the feather zone
                    final double t = (dist - innerRadius) / featherWidth;
                    final double blend = (1.0 - Math.cos(t * Math.PI)) / 2.0;
                    factor = 1.0 + blend * (fullFactor - 1.0);
                }

                if (factor == 1.0) {
                    continue;
                }

                final int idx = py * tileW + px;
                final int pixel = pixels[idx];
                final int r = clamp((int) Math.round(((pixel >> 16) & 0xFF) * factor));
                final int g = clamp((int) Math.round(((pixel >> 8) & 0xFF) * factor));
                final int b = clamp((int) Math.round((pixel & 0xFF) * factor));
                pixels[idx] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Clarity — downscale-blur-upscale glow/crispness effect
    // -------------------------------------------------------------------------

    /**
     * Apply a clarity adjustment to a full {@link BufferedImage} in place.
     * <p>
     * Negative clarity creates a dreamy glow by blending a blurred version of the image back onto
     * the original (screen blend on highlights). Positive clarity increases local contrast by
     * subtracting the blurred version (unsharp mask style).
     * <p>
     * The blur is performed at ¼ resolution (½ each dimension) to keep memory and CPU cost
     * manageable at any image size, then upscaled back before blending. This is resolution-independent
     * and produces identical visual results to full-resolution blur for the low-frequency content
     * that clarity operates on.
     *
     * @param image   TYPE_INT_RGB image, modified in place
     * @param clarity clarity amount in [-100, 100]; negative = glow/dreamy, positive = crisp/sharp
     */
    public static void applyClarity(final BufferedImage image, final double clarity) {
        if (clarity == 0.0) {
            return;
        }

        final int w = image.getWidth();
        final int h = image.getHeight();

        // Downscale to ¼ pixels for the blur pass
        final int smallW = Math.max(1, w / 2);
        final int smallH = Math.max(1, h / 2);

        final BufferedImage small = new BufferedImage(smallW, smallH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2d = small.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(image, 0, 0, smallW, smallH, null);
        g2d.dispose();

        // Gaussian blur on the small image — sigma proportional to image size, capped to avoid huge kernels
        final double sigma = Math.min(smallW, smallH) * 0.04;
        final BufferedImage blurredSmall = gaussianBlur(small, sigma);

        // Upscale blurred image back to full resolution
        final BufferedImage blurred = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2dUp = blurred.createGraphics();
        g2dUp.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2dUp.drawImage(blurredSmall, 0, 0, w, h, null);
        g2dUp.dispose();

        // Blend original with blurred
        final int[] srcPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        final int[] blurPixels = ((DataBufferInt) blurred.getRaster().getDataBuffer()).getData();
        final double amount = Math.abs(clarity) / 100.0;

        if (clarity < 0) {
            // Negative clarity: glow — screen-blend the blurred highlights onto the original
            for (int i = 0; i < srcPixels.length; i++) {
                final int sp = srcPixels[i];
                final int bp = blurPixels[i];
                final int sr = (sp >> 16) & 0xFF;
                final int sg = (sp >> 8) & 0xFF;
                final int sb = sp & 0xFF;
                final int br = (bp >> 16) & 0xFF;
                final int bg = (bp >> 8) & 0xFF;
                final int bb = bp & 0xFF;
                // Screen blend: 1 - (1-a)(1-b), then lerp by amount
                final int screenR = 255 - ((255 - sr) * (255 - br) / 255);
                final int screenG = 255 - ((255 - sg) * (255 - bg) / 255);
                final int screenB = 255 - ((255 - sb) * (255 - bb) / 255);
                final int r = clamp((int) Math.round(sr + amount * (screenR - sr)));
                final int g = clamp((int) Math.round(sg + amount * (screenG - sg)));
                final int b = clamp((int) Math.round(sb + amount * (screenB - sb)));
                srcPixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        } else {
            // Positive clarity: unsharp mask — add the difference between original and blurred
            for (int i = 0; i < srcPixels.length; i++) {
                final int sp = srcPixels[i];
                final int bp = blurPixels[i];
                final int sr = (sp >> 16) & 0xFF;
                final int sg = (sp >> 8) & 0xFF;
                final int sb = sp & 0xFF;
                final int br = (bp >> 16) & 0xFF;
                final int bg = (bp >> 8) & 0xFF;
                final int bb = bp & 0xFF;
                final int r = clamp((int) Math.round(sr + amount * (sr - br)));
                final int g = clamp((int) Math.round(sg + amount * (sg - bg)));
                final int b = clamp((int) Math.round(sb + amount * (sb - bb)));
                srcPixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * Apply a separable Gaussian blur to a TYPE_INT_RGB image.
     * Uses {@link ConvolveOp} with {@code EDGE_NO_OP} to avoid border artifacts.
     */
    private static BufferedImage gaussianBlur(final BufferedImage src, final double sigma) {
        final int radius = (int) Math.ceil(sigma * 3.0);
        final int size = 2 * radius + 1;
        final float[] kernel = new float[size];
        float sum = 0f;
        for (int i = 0; i < size; i++) {
            final double x = i - radius;
            kernel[i] = (float) Math.exp(-(x * x) / (2.0 * sigma * sigma));
            sum += kernel[i];
        }
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }

        final Kernel hKernel = new Kernel(size, 1, kernel);
        final Kernel vKernel = new Kernel(1, size, kernel);
        final ConvolveOp hBlur = new ConvolveOp(hKernel, ConvolveOp.EDGE_NO_OP, null);
        final ConvolveOp vBlur = new ConvolveOp(vKernel, ConvolveOp.EDGE_NO_OP, null);

        final BufferedImage tmp = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        final BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        hBlur.filter(src, tmp);
        vBlur.filter(tmp, dst);
        return dst;
    }

    // -------------------------------------------------------------------------
    // HSL ↔ RGB conversion
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
