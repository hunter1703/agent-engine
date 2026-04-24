package com.agentengine.runtime.tools.colorgrading;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ColorGradingUtils {

    private ColorGradingUtils() {
    }

    /**
     * Build a cosine falloff lookup table for the given hue width.
     * <p>
     * The LUT has 181 entries (indices 0-180). For index i:
     * - if i &lt;= hueWidth: cos((i / hueWidth) * (π / 2))
     * - if i &gt; hueWidth: 0.0
     *
     * @param hueWidth the half-width of the targeted hue range
     * @return a 181-element double array
     */
    public static double[] buildCosLut(final double hueWidth) {
        final double[] lut = new double[181];
        for (int i = 0; i <= 180; i++) {
            lut[i] = (i <= hueWidth)
                    ? Math.cos((i / hueWidth) * (Math.PI / 2.0))
                    : 0.0;
        }
        return lut;
    }

    /**
     * Apply all targeted adjustments to a pixel array in place.
     * <p>
     * For each pixel:
     * 1. Unpack ARGB
     * 2. Convert RGB to HSL
     * 3. Apply each adjustment in list order
     * 4. Convert HSL back to RGB
     * 5. Repack ARGB with original alpha
     *
     * @param pixels      the pixel array (ARGB ints), modified in place
     * @param adjustments the list of targeted adjustments
     * @param cosLuts     the pre-computed cosine LUTs, one per adjustment
     */
    public static void applyAdjustments(
            final int[] pixels,
            final List<ColorAdjustment> adjustments,
            final List<double[]> cosLuts) {

        for (int i = 0; i < pixels.length; i++) {
            final int pixel = pixels[i];

            // unpack ARGB
            final int a = (pixel >> 24) & 0xFF;
            final int r = (pixel >> 16) & 0xFF;
            final int g = (pixel >> 8) & 0xFF;
            final int b = pixel & 0xFF;

            // convert to HSL
            final double[] hsl = rgbToHsl(r, g, b);
            double h = hsl[0];
            double s = hsl[1];
            double l = hsl[2];

            // apply each adjustment
            for (int j = 0; j < adjustments.size(); j++) {
                final ColorAdjustment adj = adjustments.get(j);
                final double[] cosLut = cosLuts.get(j);

                // compute wrapped hue distance
                double deltaH = Math.abs(h - adj.hueCenter());
                if (deltaH > 180.0) {
                    deltaH = 360.0 - deltaH;
                }

                // look up weight
                final int lutIndex = Math.min((int) Math.round(deltaH), 180);
                final double w = cosLut[lutIndex];

                // apply adjustments
                h = ((h + adj.hueShift() * w) % 360.0 + 360.0) % 360.0;
                s = Math.clamp(s + adj.satDelta() * w * 0.01, 0.0, 1.0);
                l = Math.clamp(l + adj.lumDelta() * w * 0.01, 0.0, 1.0);
            }

            // convert back to RGB
            final int[] rgb = hslToRgb(h, s, l);

            // repack ARGB with original alpha
            pixels[i] = (a << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        }
    }

    public static List<Rectangle> buildTiles(final int width, final int height) {
        final List<Rectangle> tiles = new ArrayList<>();
        final int tileSize = 256;
        for (int y = 0; y < height; y += tileSize) {
            for (int x = 0; x < width; x += tileSize) {
                final int w = Math.min(tileSize, width - x);
                final int h = Math.min(tileSize, height - y);
                tiles.add(new Rectangle(x, y, w, h));
            }
        }
        return tiles;
    }

    public static int[] readDimensions(final File file, final String format) throws IOException {
        final ImageReader reader = getImageReader(format);
        try (final ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            reader.setInput(stream, true, true);
            return new int[]{reader.getWidth(0), reader.getHeight(0)};
        } finally {
            reader.dispose();
        }
    }

    public static ImageReader getImageReader(final String format) throws IOException {
        final Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix(format);
        if (!readers.hasNext()) {
            throw new IOException("No ImageReader found for format: " + format);
        }
        return readers.next();
    }

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
     * Convert RGB to HSL.
     *
     * @param r red component [0, 255]
     * @param g green component [0, 255]
     * @param b blue component [0, 255]
     * @return double array [h, s, l] where h is [0, 360), s and l are [0, 1]
     */
    private static double[] rgbToHsl(final int r, final int g, final int b) {
        final double rN = r / 255.0;
        final double gN = g / 255.0;
        final double bN = b / 255.0;

        final double max = Math.max(rN, Math.max(gN, bN));
        final double min = Math.min(rN, Math.min(gN, bN));
        final double delta = max - min;

        // lightness
        final double l = (max + min) / 2.0;

        // saturation
        final double s;
        if (delta == 0.0) {
            s = 0.0;
        } else {
            s = delta / (1.0 - Math.abs(2.0 * l - 1.0));
        }

        // hue
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
     * @return int array [r, g, b] where each component is [0, 255]
     */
    private static int[] hslToRgb(final double h, final double s, final double l) {
        final double c = (1.0 - Math.abs(2.0 * l - 1.0)) * s;
        final double x = c * (1.0 - Math.abs((h / 60.0) % 2.0 - 1.0));
        final double m = l - c / 2.0;

        final double r1, g1, b1;
        final int sector = (int) (h / 60.0);

        switch (sector) {
            case 0 -> { r1 = c; g1 = x; b1 = 0; }
            case 1 -> { r1 = x; g1 = c; b1 = 0; }
            case 2 -> { r1 = 0; g1 = c; b1 = x; }
            case 3 -> { r1 = 0; g1 = x; b1 = c; }
            case 4 -> { r1 = x; g1 = 0; b1 = c; }
            default -> { r1 = c; g1 = 0; b1 = x; }
        }

        final int r = clamp((int) Math.round((r1 + m) * 255.0));
        final int g = clamp((int) Math.round((g1 + m) * 255.0));
        final int b = clamp((int) Math.round((b1 + m) * 255.0));

        return new int[]{r, g, b};
    }

    private static int clamp(final int value) {
        return Math.clamp(value, 0, 255);
    }
}