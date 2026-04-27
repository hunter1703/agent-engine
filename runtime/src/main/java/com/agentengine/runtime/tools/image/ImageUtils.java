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
 *
 * <p>All pixel-level math operates in <b>linear light</b> (scene-referred linear RGB, D65).
 * sRGB-encoded pixels are decoded to linear before any adjustment and re-encoded afterward.
 * This is the only physically correct approach: gamma-space arithmetic produces wrong exposure
 * scaling, wrong tone curves, wrong saturation, and wrong vignette darkening.
 *
 * <p>Algorithm sources:
 * <ul>
 *   <li>sRGB ↔ linear: IEC 61966-2-1 (exact piecewise formula)</li>
 *   <li>Exposure: linear EV scale pow(2, ev) in linear light</li>
 *   <li>Tone curve: monotone cubic Hermite spline in linear light, ±100 → ±0.25 zone shift</li>
 *   <li>Temperature: Kang et al. (2002) Planckian locus → CIE xy → von Kries LMS adaptation → sRGB</li>
 *   <li>Saturation: sRGB → linear → Oklab → chroma scale → linear → sRGB</li>
 *   <li>HSL color mix: hue-targeted adjustments in HSL (matches Lightroom Color Mixer)</li>
 *   <li>Split toning: luminance-weighted hue tinting in HSL, zone weights from linear luminance</li>
 *   <li>Vignette: elliptical cosine falloff applied in linear light</li>
 *   <li>Clarity: Gaussian blur + unsharp mask / screen blend in linear light</li>
 * </ul>
 */
public final class ImageUtils {

    private static final int TILE_SIZE = 256;
    private static final float JPEG_OUTPUT_QUALITY = 0.95f;

    /**
     * sRGB → linear decode LUT: SRGB_TO_LINEAR[i] = linear value for sRGB byte i.
     * IEC 61966-2-1: linear = (srgb/255)^2.4 / 1.055 + 0.055/1.055 for srgb > 0.04045*255,
     * else linear = (srgb/255) / 12.92.
     */
    private static final double[] SRGB_TO_LINEAR = new double[256];

    /**
     * Linear → sRGB encode LUT: LINEAR_TO_SRGB[i] maps linear*255 index to sRGB byte.
     * Sampled at 4096 points for sub-byte accuracy, then rounded.
     */
    private static final int[] LINEAR_TO_SRGB = new int[4097];

    static {
        for (int i = 0; i < 256; i++) {
            final double s = i / 255.0;
            SRGB_TO_LINEAR[i] = (s <= 0.04045) ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        }
        for (int i = 0; i <= 4096; i++) {
            final double lin = i / 4096.0;
            final double s = (lin <= 0.0031308) ? lin * 12.92 : 1.055 * Math.pow(lin, 1.0 / 2.4) - 0.055;
            LINEAR_TO_SRGB[i] = clamp((int) Math.round(s * 255.0));
        }
    }

    /** Decode sRGB byte [0,255] to linear [0,1]. */
    private static double toLinear(final int srgb) {
        return SRGB_TO_LINEAR[srgb & 0xFF];
    }

    /** Encode linear [0,1] to sRGB byte [0,255]. */
    private static int toSrgb(final double linear) {
        final int idx = (int) Math.round(Math.clamp(linear, 0.0, 1.0) * 4096.0);
        return LINEAR_TO_SRGB[idx];
    }

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
     * Process an already-loaded {@link BufferedImage} tile-by-tile in place.
     * Used when a pre-computation step (e.g. base layer for shadows/highlights) requires the full image
     * before per-tile processing begins.
     *
     * @param image  TYPE_INT_RGB image to process in place
     * @param tileOp operation receiving (pixels, tileX, tileY, tileW, tileH)
     */
    @FunctionalInterface
    public interface InPlaceTileOperation {
        void apply(int[] pixels, int tileX, int tileY, int tileW, int tileH);
    }

    public static void processTiledInPlace(final BufferedImage image, final InPlaceTileOperation tileOp) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] destPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        final List<Rectangle> tiles = buildTiles(width, height);
        final List<Callable<Void>> callables = new ArrayList<>();
        for (final Rectangle tile : tiles) {
            callables.add(() -> {
                // Tiles are disjoint — each reads/writes a unique index range; no lock needed.
                final int[] tilePixels = new int[tile.width * tile.height];
                for (int row = 0; row < tile.height; row++) {
                    System.arraycopy(destPixels, (tile.y + row) * width + tile.x, tilePixels, row * tile.width, tile.width);
                }
                tileOp.apply(tilePixels, tile.x, tile.y, tile.width, tile.height);
                for (int row = 0; row < tile.height; row++) {
                    System.arraycopy(tilePixels, row * tile.width, destPixels, (tile.y + row) * width + tile.x, tile.width);
                }
                return null;
            });
        }
        try {
            StructuredConcurrencyUtils.runConcurrently(callables);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            final int[] destPixels = ((DataBufferInt) outputImage.getRaster().getDataBuffer()).getData();
            final List<Callable<Void>> callables = new ArrayList<>();
            for (final Rectangle tile : tiles) {
                callables.add(() -> {
                    final ImageReader reader = getImageReader(format);
                    try (final ImageInputStream stream = ImageIO.createImageInputStream(inputFile)) {
                        reader.setInput(stream, false, true);
                        final BufferedImage normalized = readAndNormalizeTile(reader, tile);

                        final int[] pixels = ((DataBufferInt) normalized.getRaster().getDataBuffer()).getData();
                        tileOp.apply(pixels, tile.x, tile.y, tile.width, tile.height, width, height);

                        // Tiles are disjoint — each writes to a unique index range; no lock needed.
                        for (int row = 0; row < tile.height; row++) {
                            System.arraycopy(pixels, row * tile.width, destPixels, (tile.y + row) * width + tile.x, tile.width);
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
            lut[i] = (i <= hueWidth) ? Math.cos((i / hueWidth) * (Math.PI / 2.0)) : 0.0;
        }
        return lut;
    }

    /**
     * Apply hue-targeted HSL color adjustments to a pixel array in place.
     *
     * <p>Decodes sRGB → linear → HSL, applies hue/saturation/luminance deltas weighted by a
     * cosine falloff around the target hue, then converts back HSL → linear → sRGB.
     * Operating through linear light ensures the HSL conversion sees perceptually correct
     * luminance values rather than gamma-compressed ones.
     *
     * <p>Matches Lightroom's Color Mixer (HSL panel) semantics:
     * hue_shift in degrees, sat_delta and lum_delta on [-100, 100].
     */
    public static void applyColorAdjustments(
            final int[] pixels,
            final List<ColorAdjustment> adjustments,
            final List<double[]> cosLuts) {

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];

            // Decode sRGB → linear → HSL
            final double rl = toLinear((pixel >> 16) & 0xFF);
            final double gl = toLinear((pixel >> 8) & 0xFF);
            final double bl = toLinear(pixel & 0xFF);
            final double[] hsl = linearRgbToHsl(rl, gl, bl);
            double h = hsl[0], s = hsl[1], l = hsl[2];

            for (int j = 0; j < adjustments.size(); j++) {
                final ColorAdjustment adj = adjustments.get(j);
                final double[] cosLut = cosLuts.get(j);

                double deltaH = Math.abs(h - adj.hueCenter());
                if (deltaH > 180.0) deltaH = 360.0 - deltaH;

                final double w = cosLut[Math.min((int) Math.round(deltaH), 180)];
                if (w == 0.0) continue;

                h = ((h + adj.hueShift() * w) % 360.0 + 360.0) % 360.0;
                s = Math.clamp(s + adj.satDelta() * w * 0.01, 0.0, 1.0);
                l = Math.clamp(l + adj.lumDelta() * w * 0.01, 0.0, 1.0);
            }

            // HSL → linear → sRGB
            final double[] rgb = hslToLinearRgb(h, s, l);
            pixels[i] = 0xFF000000
                    | (toSrgb(Math.clamp(rgb[0], 0.0, 1.0)) << 16)
                    | (toSrgb(Math.clamp(rgb[1], 0.0, 1.0)) << 8)
                    |  toSrgb(Math.clamp(rgb[2], 0.0, 1.0));
        }
    }

    // -------------------------------------------------------------------------
    // Exposure — EV scale + four-zone tone curve + saturation, all in linear light
    // -------------------------------------------------------------------------

    /**
     * Apply exposure and tone adjustments to a pixel array in place.
     *
     * <p>Pipeline (all in linear light):
     * <ol>
     *   <li>Decode sRGB → linear (IEC 61966-2-1)</li>
     *   <li>EV exposure: multiply by {@code pow(2, ev)}</li>
     *   <li>Blacks/Whites: global endpoint tone curve (monotone cubic Hermite spline)</li>
     *   <li>Shadows/Highlights: local-adaptation blend using a pre-computed Gaussian base layer</li>
     *   <li>Saturation: convert to Oklab, scale chroma, convert back</li>
     *   <li>Encode linear → sRGB</li>
     * </ol>
     *
     * <p>Shadows and Highlights use the darktable/Lightroom local-adaptation technique:
     * a Gaussian-blurred base layer is inverted and blended back via an overlay blend,
     * weighted by a luminance zone mask. This preserves local contrast (edges, texture)
     * while adjusting global tonal zones — matching Lightroom PV2012 behaviour.
     * Reference: darktable shadhi.c (GPL), He et al. Guided Image Filtering (2013).
     *
     * @param pixels    TYPE_INT_RGB packed ints, modified in place
     * @param adj       exposure parameters
     * @param baseLayer pre-computed Gaussian base layer (linear luminance per pixel, same length as pixels);
     *                  pass null if shadows and highlights are both 0
     */
    public static void applyExposureAdjustments(final int[] pixels, final ExposureAdjustment adj,
                                                 final float[] baseLayer) {
        final double evScale = (adj.exposure() != 0.0) ? Math.pow(2.0, adj.exposure()) : 1.0;
        final boolean hasEndpoints = adj.blacks() != 0 || adj.whites() != 0;
        final double[] endpointLut = hasEndpoints ? buildEndpointLut(adj) : null;
        final boolean hasShadows = adj.shadows() != 0;
        final boolean hasHighlights = adj.highlights() != 0;
        final boolean hasSat = adj.saturation() != 0;

        // Darktable compress: controls how much of the midtones are excluded from the effect.
        // We use a fixed value of 0.5 (darktable default) — the slider is not exposed to the user
        // since Lightroom doesn't have an equivalent control.
        final double compress = 0.5;

        // shadows/highlights strength: darktable scales by 2.0 so ±100 → ±2.0
        final double shadowsStrength = adj.shadows() / 100.0 * 2.0;
        final double highlightsStrength = adj.highlights() / 100.0 * 2.0;

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];

            // 1. Decode sRGB → linear
            double r = toLinear((pixel >> 16) & 0xFF);
            double g = toLinear((pixel >> 8) & 0xFF);
            double b = toLinear(pixel & 0xFF);

            // 2. EV exposure scale in linear light
            if (evScale != 1.0) {
                r = Math.clamp(r * evScale, 0.0, 1.0);
                g = Math.clamp(g * evScale, 0.0, 1.0);
                b = Math.clamp(b * evScale, 0.0, 1.0);
            }

            // 3. Blacks/Whites: global endpoint tone curve
            if (endpointLut != null) {
                r = sampleLinearLut(endpointLut, r);
                g = sampleLinearLut(endpointLut, g);
                b = sampleLinearLut(endpointLut, b);
            }

            // 4. Shadows/Highlights: local-adaptation overlay blend
            if ((hasShadows || hasHighlights) && baseLayer != null) {
                // Linear luminance of the original pixel (CIE Y)
                final double la = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                // Inverted base-layer luminance (darktable: blurred_L = 1 - base_L)
                final double lb = 1.0 - baseLayer[i];

                if (hasHighlights) {
                    // Zone mask: only affects bright pixels; compress excludes midtones
                    final double highlightsXform = Math.clamp(1.0 - lb / (1.0 - compress), 0.0, 1.0);
                    double hs2 = highlightsStrength * highlightsStrength; // 0..4
                    double laH = la;
                    while (hs2 > 0.0) {
                        final double chunk = Math.min(hs2, 1.0);
                        hs2 -= 1.0;
                        // lb for highlights: invert sign relative to shadows
                        final double lbH = (lb - 0.5) * Math.signum(-highlightsStrength) * Math.signum(1.0 - laH) + 0.5;
                        final double optrans = chunk * highlightsXform;
                        laH = overlayBlend(laH, lbH, optrans);
                    }
                    // Apply the luminance change as a uniform scale across RGB channels
                    final double scale = (la > 1e-6) ? Math.clamp(laH / la, 0.0, 4.0) : 1.0;
                    r = Math.clamp(r * scale, 0.0, 1.0);
                    g = Math.clamp(g * scale, 0.0, 1.0);
                    b = Math.clamp(b * scale, 0.0, 1.0);
                }

                if (hasShadows) {
                    final double laAfterH = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    // Zone mask: only affects dark pixels
                    final double shadowsXform = Math.clamp(
                            lb / (1.0 - compress) - compress / (1.0 - compress), 0.0, 1.0);
                    double ss2 = shadowsStrength * shadowsStrength; // 0..4
                    double laS = laAfterH;
                    while (ss2 > 0.0) {
                        final double chunk = Math.min(ss2, 1.0);
                        ss2 -= 1.0;
                        final double lbS = (lb - 0.5) * Math.signum(shadowsStrength) * Math.signum(1.0 - laS) + 0.5;
                        final double optrans = chunk * shadowsXform;
                        laS = overlayBlend(laS, lbS, optrans);
                    }
                    final double scale = (laAfterH > 1e-6) ? Math.clamp(laS / laAfterH, 0.0, 4.0) : 1.0;
                    r = Math.clamp(r * scale, 0.0, 1.0);
                    g = Math.clamp(g * scale, 0.0, 1.0);
                    b = Math.clamp(b * scale, 0.0, 1.0);
                }
            }

            // 5. Saturation via Oklab chroma scaling
            if (hasSat) {
                final double[] lab = linearRgbToOklab(r, g, b);
                final double chromaScale = 1.0 + adj.saturation() * 0.01;
                lab[1] = Math.clamp(lab[1] * chromaScale, -0.5, 0.5);
                lab[2] = Math.clamp(lab[2] * chromaScale, -0.5, 0.5);
                final double[] rgb = oklabToLinearRgb(lab[0], lab[1], lab[2]);
                r = Math.clamp(rgb[0], 0.0, 1.0);
                g = Math.clamp(rgb[1], 0.0, 1.0);
                b = Math.clamp(rgb[2], 0.0, 1.0);
            }

            // 6. Encode linear → sRGB
            pixels[i] = 0xFF000000 | (toSrgb(r) << 16) | (toSrgb(g) << 8) | toSrgb(b);
        }
    }

    /**
     * Overlay/soft-light blend: the core of darktable's shadows/highlights algorithm.
     * For la ≤ 0.5: result = 2 * la * lb
     * For la > 0.5: result = 1 - 2 * (1 - la) * (1 - lb)
     * Blended with the original by {@code optrans}.
     */
    private static double overlayBlend(final double la, final double lb, final double optrans) {
        final double blended = (la > 0.5)
                ? 1.0 - (1.0 - 2.0 * (la - 0.5)) * (1.0 - lb)
                : 2.0 * la * lb;
        return la * (1.0 - optrans) + blended * optrans;
    }

    /**
     * Build a 4097-entry tone curve LUT for Blacks and Whites endpoint controls only.
     * Shadows and Highlights are handled separately via local adaptation.
     */
    private static double[] buildEndpointLut(final ExposureAdjustment adj) {
        // Blacks: negative = raise black clipping point; positive = lift black floor
        final double blackFloor = Math.clamp(adj.blacks() * (0.25 / 100.0), -0.25, 0.25);
        // Whites: positive = raise white ceiling; negative = lower white ceiling
        final double whiteCeil = Math.clamp(1.0 + adj.whites() * (0.25 / 100.0), 0.75, 1.25);

        // Zone midpoint offsets for blacks/whites zones only (shadows/highlights handled separately)
        final double zoneScale = 0.20 / 100.0;
        final double[] px = {0.000, 0.125, 0.375, 0.625, 0.875, 1.000};
        final double[] py = {
            Math.clamp(blackFloor, 0.0, 0.5),
            Math.clamp(0.125 + adj.blacks() * zoneScale, 0.0, 1.0),
            0.375,  // shadows handled by local adaptation — identity here
            0.625,  // highlights handled by local adaptation — identity here
            Math.clamp(0.875 + adj.whites() * zoneScale, 0.0, 1.0),
            Math.clamp(whiteCeil, 0.5, 1.0)
        };

        for (int i = 1; i < py.length; i++) py[i] = Math.max(py[i], py[i - 1]);

        // Fritsch-Carlson monotone cubic Hermite
        final int n = px.length;
        final double[] m = new double[n];
        final double[] delta = new double[n - 1];
        for (int i = 0; i < n - 1; i++) delta[i] = (py[i + 1] - py[i]) / (px[i + 1] - px[i]);
        m[0] = delta[0];
        m[n - 1] = delta[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = (delta[i - 1] + delta[i]) / 2.0;
        for (int i = 0; i < n - 1; i++) {
            if (delta[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; }
            else {
                final double alpha = m[i] / delta[i], beta = m[i + 1] / delta[i];
                final double tau = alpha * alpha + beta * beta;
                if (tau > 9.0) { final double s = 3.0 / Math.sqrt(tau); m[i] = s * alpha * delta[i]; m[i + 1] = s * beta * delta[i]; }
            }
        }

        final double[] lut = new double[4097];
        for (int i = 0; i <= 4096; i++) {
            final double x = i / 4096.0;
            int seg = n - 2;
            for (int k = 0; k < n - 1; k++) { if (x <= px[k + 1]) { seg = k; break; } }
            final double h = px[seg + 1] - px[seg];
            final double t = (x - px[seg]) / h;
            final double t2 = t * t, t3 = t2 * t;
            lut[i] = Math.clamp(
                    (2 * t3 - 3 * t2 + 1) * py[seg] + (t3 - 2 * t2 + t) * m[seg] * h
                    + (-2 * t3 + 3 * t2) * py[seg + 1] + (t3 - t2) * m[seg + 1] * h,
                    0.0, 1.0);
        }
        for (int i = 1; i <= 4096; i++) lut[i] = Math.max(lut[i], lut[i - 1]);
        return lut;
    }

    /** Sample a 4097-entry linear [0,1]→[0,1] LUT with linear interpolation. */
    private static double sampleLinearLut(final double[] lut, final double x) {
        final double scaled = Math.clamp(x, 0.0, 1.0) * 4096.0;
        final int lo = (int) scaled;
        final int hi = Math.min(lo + 1, 4096);
        final double frac = scaled - lo;
        return lut[lo] + frac * (lut[hi] - lut[lo]);
    }

    // -------------------------------------------------------------------------
    // Base layer computation for shadows/highlights local adaptation
    // -------------------------------------------------------------------------

    /**
     * Compute the Gaussian base layer for the shadows/highlights local-adaptation algorithm.
     *
     * <p>The base layer is the linear CIE Y luminance of each pixel after a large-radius
     * Gaussian blur. It captures the large-scale tonal structure of the image without
     * fine detail. The shadows/highlights blend uses this to determine how much each pixel
     * should be adjusted: dark pixels in the base layer get the shadow treatment, bright
     * pixels get the highlight treatment.
     *
     * <p>The blur radius is fixed at 10% of the shorter image dimension, matching
     * darktable's default radius of 100px on a typical image. This is large enough to
     * capture global tonal zones while remaining fast via the half-resolution trick.
     *
     * @param image full TYPE_INT_RGB image (not tiled — requires global context)
     * @return float array of linear luminance values [0,1], one per pixel, row-major
     */
    public static float[] computeBaseLayer(final BufferedImage image) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int[] srcPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // Extract linear luminance from each pixel
        final float[] lum = new float[w * h];
        for (int i = 0; i < srcPixels.length; i++) {
            final int p = srcPixels[i];
            final double r = toLinear((p >> 16) & 0xFF);
            final double g = toLinear((p >> 8) & 0xFF);
            final double b = toLinear(p & 0xFF);
            lum[i] = (float) (0.2126 * r + 0.7152 * g + 0.0722 * b);
        }

        // Gaussian blur at half resolution for efficiency (darktable approach)
        final int smallW = Math.max(1, w / 2);
        final int smallH = Math.max(1, h / 2);
        final double sigma = Math.min(smallW, smallH) * 0.10; // 10% of shorter dimension

        // Downsample luminance to half resolution
        final float[] small = new float[smallW * smallH];
        for (int sy = 0; sy < smallH; sy++) {
            for (int sx = 0; sx < smallW; sx++) {
                final double fx = (sx + 0.5) * (double) w / smallW - 0.5;
                final double fy = (sy + 0.5) * (double) h / smallH - 0.5;
                final int x0 = Math.clamp((int) fx, 0, w - 1);
                final int x1 = Math.clamp(x0 + 1, 0, w - 1);
                final int y0 = Math.clamp((int) fy, 0, h - 1);
                final int y1 = Math.clamp(y0 + 1, 0, h - 1);
                final double wx = fx - x0, wy = fy - y0;
                small[sy * smallW + sx] = (float) (
                        lum[y0 * w + x0] * (1 - wx) * (1 - wy)
                      + lum[y0 * w + x1] * wx * (1 - wy)
                      + lum[y1 * w + x0] * (1 - wx) * wy
                      + lum[y1 * w + x1] * wx * wy);
            }
        }

        // Gaussian blur on the small luminance image
        final float[] blurredSmall = gaussianBlurFloat(small, smallW, smallH, sigma);

        // Upsample back to full resolution (bilinear)
        final float[] base = new float[w * h];
        for (int fy = 0; fy < h; fy++) {
            for (int fx = 0; fx < w; fx++) {
                final double sx = (fx + 0.5) * (double) smallW / w - 0.5;
                final double sy = (fy + 0.5) * (double) smallH / h - 0.5;
                final int x0 = Math.clamp((int) sx, 0, smallW - 1);
                final int x1 = Math.clamp(x0 + 1, 0, smallW - 1);
                final int y0 = Math.clamp((int) sy, 0, smallH - 1);
                final int y1 = Math.clamp(y0 + 1, 0, smallH - 1);
                final double wx = sx - x0, wy = sy - y0;
                base[fy * w + fx] = (float) (
                        blurredSmall[y0 * smallW + x0] * (1 - wx) * (1 - wy)
                      + blurredSmall[y0 * smallW + x1] * wx * (1 - wy)
                      + blurredSmall[y1 * smallW + x0] * (1 - wx) * wy
                      + blurredSmall[y1 * smallW + x1] * wx * wy);
            }
        }
        return base;
    }

    /** Separable Gaussian blur on a single-channel float array with clamp-to-edge boundary. */
    private static float[] gaussianBlurFloat(final float[] src, final int w, final int h, final double sigma) {
        final int radius = (int) Math.ceil(sigma * 3.0);
        final int size = 2 * radius + 1;
        final double[] kernel = new double[size];
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            final double x = i - radius;
            kernel[i] = Math.exp(-(x * x) / (2.0 * sigma * sigma));
            sum += kernel[i];
        }
        for (int i = 0; i < size; i++) kernel[i] /= sum;

        final float[] tmp = new float[w * h];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                double v = 0;
                for (int k = 0; k < size; k++) {
                    v += src[row * w + Math.clamp(col + k - radius, 0, w - 1)] * kernel[k];
                }
                tmp[row * w + col] = (float) v;
            }
        }
        final float[] dst = new float[w * h];
        for (int col = 0; col < w; col++) {
            for (int row = 0; row < h; row++) {
                double v = 0;
                for (int k = 0; k < size; k++) {
                    v += tmp[Math.clamp(row + k - radius, 0, h - 1) * w + col] * kernel[k];
                }
                dst[row * w + col] = (float) v;
            }
        }
        return dst;
    }

    // -------------------------------------------------------------------------
    // Temperature and tint — Planckian locus + von Kries chromatic adaptation
    // -------------------------------------------------------------------------

    /**
     * Apply white balance correction to a pixel array in place.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Convert target Kelvin to CIE xy chromaticity using the Kang et al. (2002) formula
     *       (exact Planckian locus fit, valid 1667K–25000K)</li>
     *   <li>Apply tint as a perpendicular shift on the Planckian locus in CIE uv space</li>
     *   <li>Compute von Kries chromatic adaptation from D65 (the sRGB white point) to the
     *       target illuminant in CAT02 LMS space</li>
     *   <li>Convert the resulting 3×3 adaptation matrix to linear sRGB channel multipliers</li>
     *   <li>Decode sRGB → linear, apply multipliers, encode linear → sRGB</li>
     * </ol>
     *
     * <p>This is the same pipeline used by dcraw, RawTherapee, and darktable for rendered images.
     */
    public static void applyTemperatureAdjustment(final int[] pixels, final TemperatureAdjustment adj) {
        // 0 means "not set" — default to D65 (6500K), the sRGB white point, which is a no-op.
        final int kelvin = (adj.temperature() == 0) ? 6500 : Math.clamp(adj.temperature(), 1667, 25000);
        if (kelvin == 6500 && adj.tint() == 0) {
            return;
        }

        // --- Step 1: Kelvin → CIE xy (Kang et al. 2002, Eq. 1-4) ---
        final double T = kelvin;
        final double T2 = T * T, T3 = T2 * T;
        final double xc;
        if (T <= 4000) {
            xc = -0.2661239e9 / T3 - 0.2343580e6 / T2 + 0.8776956e3 / T + 0.179910;
        } else {
            xc = -3.0258469e9 / T3 + 2.1070379e6 / T2 + 0.2226347e3 / T + 0.240390;
        }
        final double xc2 = xc * xc, xc3 = xc2 * xc;
        final double yc;
        if (T <= 2222) {
            yc = -1.1063814 * xc3 - 1.34811020 * xc2 + 2.18555832 * xc - 0.20219683;
        } else if (T <= 4000) {
            yc = -0.9549476 * xc3 - 1.37418593 * xc2 + 2.09137015 * xc - 0.16748867;
        } else {
            yc = 3.0817580 * xc3 - 5.87338670 * xc2 + 3.75112997 * xc - 0.37001483;
        }

        // --- Step 2: Apply tint as CIE uv shift perpendicular to locus ---
        // Convert xy → uv (CIE 1960 UCS)
        final double denom = -2.0 * xc + 12.0 * yc + 3.0;
        double u = 4.0 * xc / denom;
        double v = 6.0 * yc / denom;
        // Tint shifts v (green-magenta axis in uv); ±150 → ±0.015 in uv
        v += adj.tint() * (0.015 / 150.0);
        // Convert uv → xy (CIE 1960 UCS inverse)
        final double uvDenom = 2.0 * u - 8.0 * v + 4.0;
        final double x = 3.0 * u / uvDenom;
        final double y = 2.0 * v / uvDenom;

        // --- Step 3: xy → XYZ (Y=1) ---
        final double Xw = x / y;
        final double Yw = 1.0;
        final double Zw = (1.0 - x - y) / y;

        // D65 white point XYZ
        final double Xd65 = 0.95047, Yd65 = 1.00000, Zd65 = 1.08883;

        // --- Step 4: Von Kries adaptation in CAT02 LMS space ---
        // CAT02 matrix (XYZ → LMS)
        final double[] Lw = xyzToLmsCat02(Xw, Yw, Zw);
        final double[] Ld65 = xyzToLmsCat02(Xd65, Yd65, Zd65);

        // Diagonal gain: scale each LMS channel so source white maps to D65 white
        final double gainL = Ld65[0] / Lw[0];
        final double gainM = Ld65[1] / Lw[1];
        final double gainS = Ld65[2] / Lw[2];

        // Build the full 3×3 adaptation matrix in XYZ space:
        // M_adapt = CAT02_inv * diag(gain) * CAT02
        // Then convert to linear sRGB: M_srgb = M_xyz_to_srgb * M_adapt * M_srgb_to_xyz
        // Pre-multiply into per-pixel RGB multipliers via the combined matrix.
        final double[] rRow = adaptedSrgbRow(0, gainL, gainM, gainS);
        final double[] gRow = adaptedSrgbRow(1, gainL, gainM, gainS);
        final double[] bRow = adaptedSrgbRow(2, gainL, gainM, gainS);

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];
            final double rl = toLinear((pixel >> 16) & 0xFF);
            final double gl = toLinear((pixel >> 8) & 0xFF);
            final double bl = toLinear(pixel & 0xFF);

            final double ro = Math.clamp(rRow[0] * rl + rRow[1] * gl + rRow[2] * bl, 0.0, 1.0);
            final double go = Math.clamp(gRow[0] * rl + gRow[1] * gl + gRow[2] * bl, 0.0, 1.0);
            final double bo = Math.clamp(bRow[0] * rl + bRow[1] * gl + bRow[2] * bl, 0.0, 1.0);

            pixels[i] = 0xFF000000 | (toSrgb(ro) << 16) | (toSrgb(go) << 8) | toSrgb(bo);
        }
    }

    /** XYZ → CAT02 LMS. */
    private static double[] xyzToLmsCat02(final double X, final double Y, final double Z) {
        return new double[]{
             0.7328 * X + 0.4296 * Y - 0.1624 * Z,
            -0.7036 * X + 1.6975 * Y + 0.0061 * Z,
             0.0030 * X + 0.0136 * Y + 0.9834 * Z
        };
    }

    /**
     * Compute one row of the combined chromatic adaptation matrix in linear sRGB space.
     *
     * <p>The full pipeline is: linear sRGB → XYZ (D65) → CAT02 LMS → diagonal gain →
     * CAT02⁻¹ LMS → XYZ (D65) → linear sRGB. Pre-multiplied into a single 3×3 matrix.
     *
     * @param row    output channel index (0=R, 1=G, 2=B)
     * @param gainL  LMS L-channel gain
     * @param gainM  LMS M-channel gain
     * @param gainS  LMS S-channel gain
     * @return 3-element row [rCoeff, gCoeff, bCoeff]
     */
    private static double[] adaptedSrgbRow(final int row, final double gainL, final double gainM, final double gainS) {
        // sRGB (linear, D65) → XYZ D65 (IEC 61966-2-1)
        final double[][] srgbToXyz = {
            {0.4124564, 0.3575761, 0.1804375},
            {0.2126729, 0.7151522, 0.0721750},
            {0.0193339, 0.1191920, 0.9503041}
        };
        // XYZ D65 → linear sRGB (IEC 61966-2-1)
        final double[][] xyzToSrgb = {
            { 3.2404542, -1.5371385, -0.4985314},
            {-0.9692660,  1.8760108,  0.0415560},
            { 0.0556434, -0.2040259,  1.0572252}
        };
        // CAT02: XYZ → LMS
        final double[][] cat02 = {
            { 0.7328,  0.4296, -0.1624},
            {-0.7036,  1.6975,  0.0061},
            { 0.0030,  0.0136,  0.9834}
        };
        // CAT02 inverse: LMS → XYZ
        final double[][] cat02inv = {
            { 1.0961238, -0.2788690,  0.1827452},
            { 0.4543690,  0.4735332,  0.0720978},
            {-0.0096276, -0.0056980,  1.0153256}
        };

        // For each input sRGB channel, compute the output sRGB value for the given row.
        // M_full = xyzToSrgb * cat02inv * diag(gainL, gainM, gainS) * cat02 * srgbToXyz
        final double[] result = new double[3];
        for (int inCh = 0; inCh < 3; inCh++) {
            // sRGB[inCh] → XYZ
            double X = srgbToXyz[0][inCh];
            double Y = srgbToXyz[1][inCh];
            double Z = srgbToXyz[2][inCh];
            // XYZ → LMS (CAT02)
            double L = cat02[0][0] * X + cat02[0][1] * Y + cat02[0][2] * Z;
            double M = cat02[1][0] * X + cat02[1][1] * Y + cat02[1][2] * Z;
            double S = cat02[2][0] * X + cat02[2][1] * Y + cat02[2][2] * Z;
            // Apply diagonal gain
            L *= gainL; M *= gainM; S *= gainS;
            // LMS → XYZ (CAT02 inverse)
            X = cat02inv[0][0] * L + cat02inv[0][1] * M + cat02inv[0][2] * S;
            Y = cat02inv[1][0] * L + cat02inv[1][1] * M + cat02inv[1][2] * S;
            Z = cat02inv[2][0] * L + cat02inv[2][1] * M + cat02inv[2][2] * S;
            // XYZ → sRGB[row]
            result[inCh] = xyzToSrgb[row][0] * X + xyzToSrgb[row][1] * Y + xyzToSrgb[row][2] * Z;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Split toning — luminance-weighted hue tinting in HSL
    // -------------------------------------------------------------------------

    /**
     * Apply split toning to a pixel array in place.
     *
     * <p>Zone weights are computed from the pixel's linear luminance (CIE Y), which is
     * perceptually correct — gamma-encoded L would compress the shadow region and expand
     * highlights, making the crossover point feel wrong.
     *
     * <p>The tinting itself operates in HSL space (hue blend + saturation introduction),
     * which matches Lightroom's Color Grading panel behaviour.
     */
    public static void applySplitTone(final int[] pixels, final SplitToneAdjustment adj) {
        if (adj.shadowSaturation() == 0 && adj.highlightSaturation() == 0) {
            return;
        }

        // Crossover midpoint in linear luminance: +100 → 0.75, -100 → 0.25
        final double midpoint = 0.5 + adj.balance() * 0.0025;
        final double shadowStrength = adj.shadowSaturation() / 100.0;
        final double highlightStrength = adj.highlightSaturation() / 100.0;

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];

            // Decode to linear for luminance computation
            final double rl = toLinear((pixel >> 16) & 0xFF);
            final double gl = toLinear((pixel >> 8) & 0xFF);
            final double bl = toLinear(pixel & 0xFF);

            // CIE Y (linear luminance) — perceptually correct zone weighting
            final double Y = 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;

            // Convert to HSL for tinting (HSL operates on gamma-encoded values for
            // correct hue representation, so re-encode first)
            final double[] hsl = linearRgbToHsl(rl, gl, bl);
            double h = hsl[0], s = hsl[1], l = hsl[2];

            if (Y <= midpoint && shadowStrength > 0.0) {
                final double t = (midpoint - Y) / midpoint;
                final double w = shadowStrength * (1.0 - Math.cos(t * Math.PI / 2.0));
                h = blendHue(h, adj.shadowHue(), w);
                s = Math.clamp(s + w * (1.0 - s) * 0.5, 0.0, 1.0);
            }

            if (Y > midpoint && highlightStrength > 0.0) {
                final double t = (Y - midpoint) / (1.0 - midpoint);
                final double w = highlightStrength * (1.0 - Math.cos(t * Math.PI / 2.0));
                h = blendHue(h, adj.highlightHue(), w);
                s = Math.clamp(s + w * (1.0 - s) * 0.5, 0.0, 1.0);
            }

            final double[] rgb = hslToLinearRgb(h, s, l);
            pixels[i] = 0xFF000000
                    | (toSrgb(Math.clamp(rgb[0], 0.0, 1.0)) << 16)
                    | (toSrgb(Math.clamp(rgb[1], 0.0, 1.0)) << 8)
                    |  toSrgb(Math.clamp(rgb[2], 0.0, 1.0));
        }
    }

    private static double blendHue(final double from, final double to, final double w) {
        double delta = to - from;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return ((from + delta * w) % 360.0 + 360.0) % 360.0;
    }

    // -------------------------------------------------------------------------
    // Vignette — elliptical cosine falloff in linear light
    // -------------------------------------------------------------------------

    /**
     * Apply a vignette effect to a tile in place.
     *
     * <p>The brightness multiplier and saturation adjustment are applied in <b>linear light</b>.
     *
     * <p>Saturation algorithm from darktable vignette.c (GPL):
     * {@code col[c] = col[c] - (mean - col[c]) * weight * saturation}
     * where mean = (R+G+B)/3. Negative saturation pushes channels toward the mean (desaturates);
     * positive pushes away (saturates).
     */
    public static void applyVignette(
            final int[] pixels,
            final int tileX, final int tileY, final int tileW, final int tileH,
            final int imageW, final int imageH,
            final VignetteAdjustment adj) {

        if (adj.amount() == 0 && adj.saturation() == 0) {
            return;
        }

        final double cx = imageW / 2.0;
        final double cy = imageH / 2.0;

        final double innerRadius = (adj.midpoint() <= 0 ? 50.0 : adj.midpoint()) / 100.0;
        final double featherWidth = Math.max(0.01, (adj.feather() <= 0 ? 50.0 : adj.feather()) / 100.0) * 0.5;
        final double outerRadius = innerRadius + featherWidth;

        // In linear light: amount=-100 → factor=0.0 (black), amount=+100 → factor=2.0 (white)
        final double fullFactor = 1.0 + adj.amount() / 100.0;
        // Saturation: -100 → fully desaturate edges, +100 → fully saturate edges
        final double satStrength = adj.saturation() / 100.0;

        for (int py = 0; py < tileH; py++) {
            for (int px = 0; px < tileW; px++) {
                final double dx = (tileX + px - cx) / cx;
                final double dy = (tileY + py - cy) / cy;
                final double dist = Math.sqrt(dx * dx + dy * dy);

                final double weight;
                if (dist <= innerRadius) {
                    weight = 0.0;
                } else if (dist >= outerRadius) {
                    weight = 1.0;
                } else {
                    final double t = (dist - innerRadius) / featherWidth;
                    weight = (1.0 - Math.cos(t * Math.PI)) / 2.0;
                }

                if (weight == 0.0) continue;

                final int idx = py * tileW + px;
                final int pixel = pixels[idx];

                // Decode → apply in linear → encode
                double r = toLinear((pixel >> 16) & 0xFF);
                double g = toLinear((pixel >> 8) & 0xFF);
                double b = toLinear(pixel & 0xFF);

                // 1. Brightness adjustment (darktable: negative = multiply, positive = add)
                if (adj.amount() != 0) {
                    if (adj.amount() < 0) {
                        // Darktable: falloff = 1 + weight * brightness (brightness is negative)
                        final double falloff = 1.0 + weight * (adj.amount() / 100.0);
                        r = Math.clamp(r * falloff, 0.0, 1.0);
                        g = Math.clamp(g * falloff, 0.0, 1.0);
                        b = Math.clamp(b * falloff, 0.0, 1.0);
                    } else {
                        // Darktable: falloff = weight * brightness (brightness is positive)
                        final double falloff = weight * (adj.amount() / 100.0);
                        r = Math.clamp(r + falloff, 0.0, 1.0);
                        g = Math.clamp(g + falloff, 0.0, 1.0);
                        b = Math.clamp(b + falloff, 0.0, 1.0);
                    }
                }

                // 2. Saturation adjustment (darktable algorithm: push toward/away from mean)
                if (adj.saturation() != 0) {
                    final double mean = (r + g + b) / 3.0;
                    final double wss = weight * satStrength;
                    r = Math.clamp(r - (mean - r) * wss, 0.0, 1.0);
                    g = Math.clamp(g - (mean - g) * wss, 0.0, 1.0);
                    b = Math.clamp(b - (mean - b) * wss, 0.0, 1.0);
                }

                pixels[idx] = 0xFF000000 | (toSrgb(r) << 16) | (toSrgb(g) << 8) | toSrgb(b);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Clarity — local contrast / glow in linear light
    // -------------------------------------------------------------------------

    /**
     * Apply a clarity adjustment to a full {@link BufferedImage} in place.
     *
     * <p>The blur and blend operate in <b>linear light</b>. Blurring in gamma space produces
     * incorrect halos because the gamma curve compresses bright regions — a bright edge next to
     * a dark region blurs to a value that is too dark in linear terms, causing the unsharp mask
     * to over-sharpen bright edges and under-sharpen dark ones.
     *
     * <p>Positive clarity: unsharp mask (local contrast boost).
     * Negative clarity: screen blend of blurred image (dreamy glow / mist).
     */
    public static void applyClarity(final BufferedImage image, final double clarity) {
        if (clarity == 0.0) {
            return;
        }

        final int w = image.getWidth();
        final int h = image.getHeight();
        final int[] srcPixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // Decode sRGB → linear float array
        final float[] lin = new float[srcPixels.length * 3];
        for (int i = 0; i < srcPixels.length; i++) {
            final int p = srcPixels[i];
            lin[i * 3]     = (float) toLinear((p >> 16) & 0xFF);
            lin[i * 3 + 1] = (float) toLinear((p >> 8) & 0xFF);
            lin[i * 3 + 2] = (float) toLinear(p & 0xFF);
        }

        // Gaussian blur in linear space at half resolution
        final int smallW = Math.max(1, w / 2);
        final int smallH = Math.max(1, h / 2);
        final float[] small = downsampleLinear(lin, w, h, smallW, smallH);
        final double sigma = Math.min(smallW, smallH) * 0.04;
        final float[] blurredSmall = gaussianBlurLinear(small, smallW, smallH, sigma);
        final float[] blurred = upsampleLinear(blurredSmall, smallW, smallH, w, h);

        final double amount = Math.abs(clarity) / 100.0;

        if (clarity < 0) {
            // Negative clarity: screen blend of blurred onto original in linear light
            for (int i = 0; i < srcPixels.length; i++) {
                final float sr = lin[i * 3], sg = lin[i * 3 + 1], sb = lin[i * 3 + 2];
                final float br = blurred[i * 3], bg = blurred[i * 3 + 1], bb = blurred[i * 3 + 2];
                // Screen: 1 - (1-a)(1-b)
                final double ro = sr + amount * ((1.0 - (1.0 - sr) * (1.0 - br)) - sr);
                final double go = sg + amount * ((1.0 - (1.0 - sg) * (1.0 - bg)) - sg);
                final double bo = sb + amount * ((1.0 - (1.0 - sb) * (1.0 - bb)) - sb);
                srcPixels[i] = 0xFF000000
                        | (toSrgb(Math.clamp(ro, 0.0, 1.0)) << 16)
                        | (toSrgb(Math.clamp(go, 0.0, 1.0)) << 8)
                        |  toSrgb(Math.clamp(bo, 0.0, 1.0));
            }
        } else {
            // Positive clarity: unsharp mask in linear light
            for (int i = 0; i < srcPixels.length; i++) {
                final float sr = lin[i * 3], sg = lin[i * 3 + 1], sb = lin[i * 3 + 2];
                final float br = blurred[i * 3], bg = blurred[i * 3 + 1], bb = blurred[i * 3 + 2];
                final double ro = sr + amount * (sr - br);
                final double go = sg + amount * (sg - bg);
                final double bo = sb + amount * (sb - bb);
                srcPixels[i] = 0xFF000000
                        | (toSrgb(Math.clamp(ro, 0.0, 1.0)) << 16)
                        | (toSrgb(Math.clamp(go, 0.0, 1.0)) << 8)
                        |  toSrgb(Math.clamp(bo, 0.0, 1.0));
            }
        }
    }

    /** Bilinear downsample of a linear float RGB array. */
    private static float[] downsampleLinear(final float[] src, final int sw, final int sh,
                                             final int dw, final int dh) {
        final float[] dst = new float[dw * dh * 3];
        final double sx = (double) sw / dw;
        final double sy = (double) sh / dh;
        for (int dy = 0; dy < dh; dy++) {
            for (int dx = 0; dx < dw; dx++) {
                final double fx = (dx + 0.5) * sx - 0.5;
                final double fy = (dy + 0.5) * sy - 0.5;
                final int x0 = Math.clamp((int) fx, 0, sw - 1);
                final int x1 = Math.clamp(x0 + 1, 0, sw - 1);
                final int y0 = Math.clamp((int) fy, 0, sh - 1);
                final int y1 = Math.clamp(y0 + 1, 0, sh - 1);
                final double wx = fx - x0, wy = fy - y0;
                for (int c = 0; c < 3; c++) {
                    dst[(dy * dw + dx) * 3 + c] = (float) (
                            src[(y0 * sw + x0) * 3 + c] * (1 - wx) * (1 - wy)
                          + src[(y0 * sw + x1) * 3 + c] * wx * (1 - wy)
                          + src[(y1 * sw + x0) * 3 + c] * (1 - wx) * wy
                          + src[(y1 * sw + x1) * 3 + c] * wx * wy);
                }
            }
        }
        return dst;
    }

    /** Bilinear upsample of a linear float RGB array. */
    private static float[] upsampleLinear(final float[] src, final int sw, final int sh,
                                           final int dw, final int dh) {
        return downsampleLinear(src, sw, sh, dw, dh);
    }

    /** Separable Gaussian blur on a linear float RGB array with clamp-to-edge boundary. */
    private static float[] gaussianBlurLinear(final float[] src, final int w, final int h, final double sigma) {
        final int radius = (int) Math.ceil(sigma * 3.0);
        final int size = 2 * radius + 1;
        final double[] kernel = new double[size];
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            final double x = i - radius;
            kernel[i] = Math.exp(-(x * x) / (2.0 * sigma * sigma));
            sum += kernel[i];
        }
        for (int i = 0; i < size; i++) kernel[i] /= sum;

        final float[] tmp = new float[w * h * 3];
        // Horizontal pass
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                double r = 0, g = 0, b = 0;
                for (int k = 0; k < size; k++) {
                    final int sc = Math.clamp(col + k - radius, 0, w - 1);
                    r += src[(row * w + sc) * 3]     * kernel[k];
                    g += src[(row * w + sc) * 3 + 1] * kernel[k];
                    b += src[(row * w + sc) * 3 + 2] * kernel[k];
                }
                tmp[(row * w + col) * 3]     = (float) r;
                tmp[(row * w + col) * 3 + 1] = (float) g;
                tmp[(row * w + col) * 3 + 2] = (float) b;
            }
        }
        final float[] dst = new float[w * h * 3];
        // Vertical pass
        for (int col = 0; col < w; col++) {
            for (int row = 0; row < h; row++) {
                double r = 0, g = 0, b = 0;
                for (int k = 0; k < size; k++) {
                    final int sr = Math.clamp(row + k - radius, 0, h - 1);
                    r += tmp[(sr * w + col) * 3]     * kernel[k];
                    g += tmp[(sr * w + col) * 3 + 1] * kernel[k];
                    b += tmp[(sr * w + col) * 3 + 2] * kernel[k];
                }
                dst[(row * w + col) * 3]     = (float) r;
                dst[(row * w + col) * 3 + 1] = (float) g;
                dst[(row * w + col) * 3 + 2] = (float) b;
            }
        }
        return dst;
    }

    // -------------------------------------------------------------------------
    // Color space conversions
    // -------------------------------------------------------------------------

    /**
     * Linear RGB → HSL. Input channels in [0,1], output h ∈ [0,360), s and l ∈ [0,1].
     * HSL is computed directly from linear RGB values — this is correct for hue targeting
     * because hue is a geometric property of the RGB cube, independent of gamma.
     */
    private static double[] linearRgbToHsl(final double r, final double g, final double b) {
        final double max = Math.max(r, Math.max(g, b));
        final double min = Math.min(r, Math.min(g, b));
        final double delta = max - min;
        final double l = (max + min) / 2.0;
        final double s = (delta == 0.0) ? 0.0 : delta / (1.0 - Math.abs(2.0 * l - 1.0));
        final double h;
        if (delta == 0.0) {
            h = 0.0;
        } else if (max == r) {
            h = 60.0 * (((g - b) / delta) % 6.0);
        } else if (max == g) {
            h = 60.0 * ((b - r) / delta + 2.0);
        } else {
            h = 60.0 * ((r - g) / delta + 4.0);
        }
        return new double[]{h < 0.0 ? h + 360.0 : h, s, l};
    }

    /** HSL → linear RGB. h ∈ [0,360), s and l ∈ [0,1]. Returns linear RGB in [0,1]. */
    private static double[] hslToLinearRgb(final double h, final double s, final double l) {
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
        return new double[]{r1 + m, g1 + m, b1 + m};
    }

    /**
     * Linear sRGB → Oklab (Björn Ottosson, 2020).
     * Returns [L, a, b] where L ∈ [0,1], a and b ∈ [-0.5, 0.5] approximately.
     * Oklab is perceptually uniform: equal Euclidean distances correspond to equal perceived
     * colour differences, and chroma scaling does not shift hue.
     */
    private static double[] linearRgbToOklab(final double r, final double g, final double b) {
        // Linear sRGB → LMS (Oklab M1 matrix)
        final double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
        final double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
        final double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;
        // Cube root (non-linear response)
        final double lc = Math.cbrt(l);
        final double mc = Math.cbrt(m);
        final double sc = Math.cbrt(s);
        // LMS' → Lab (Oklab M2 matrix)
        return new double[]{
            0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc,
            1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc,
            0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc
        };
    }

    /**
     * Oklab → linear sRGB (Björn Ottosson, 2020).
     * Returns linear RGB; caller must clamp to [0,1] before encoding.
     */
    private static double[] oklabToLinearRgb(final double L, final double a, final double b) {
        // Lab → LMS' (Oklab M2 inverse)
        final double lc = L + 0.3963377774 * a + 0.2158037573 * b;
        final double mc = L - 0.1055613458 * a - 0.0638541728 * b;
        final double sc = L - 0.0894841775 * a - 1.2914855480 * b;
        // Cube (inverse of cube root)
        final double l = lc * lc * lc;
        final double m = mc * mc * mc;
        final double s = sc * sc * sc;
        // LMS → linear sRGB (Oklab M1 inverse)
        return new double[]{
             4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        };
    }

    private static int clamp(final int value) {
        return Math.clamp(value, 0, 255);
    }
}
