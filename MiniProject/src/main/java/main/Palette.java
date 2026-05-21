package main;

import java.awt.Color;
import java.util.Objects;

public class Palette {
    private static final int INTERIOR_COLOR = Color.BLACK.getRGB();

    private final String name;
    private final int[] colors;

    public Palette(String name, Color[] colors) {
        this.name = Objects.requireNonNullElse(name, "Palette");
        if (colors == null || colors.length == 0) {
            this.colors = new int[]{Color.BLACK.getRGB(), Color.WHITE.getRGB()};
            return;
        }

        this.colors = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            this.colors[i] = colors[i].getRGB();
        }
    }

    public String getName() {
        return name;
    }

    public int getColor(int iterations, int maxIterations, double smoothIteration, boolean smooth) {
        if (iterations >= maxIterations) {
            return INTERIOR_COLOR;
        }

        if (!smooth) {
            return colors[iterations % colors.length];
        }

        double cycle = (Math.max(0.0, smoothIteration) / maxIterations * colors.length * 10.0) % colors.length;
        int index1 = (int) Math.floor(cycle);
        int index2 = (index1 + 1) % colors.length;
        double fraction = cycle - index1;
        return blend(colors[index1], colors[index2], fraction);
    }

    private static int blend(int colorA, int colorB, double fraction) {
        int red = interpolate((colorA >> 16) & 0xFF, (colorB >> 16) & 0xFF, fraction);
        int green = interpolate((colorA >> 8) & 0xFF, (colorB >> 8) & 0xFF, fraction);
        int blue = interpolate(colorA & 0xFF, colorB & 0xFF, fraction);
        return (red << 16) | (green << 8) | blue;
    }

    private static int interpolate(int start, int end, double fraction) {
        return (int) Math.round(start + (end - start) * fraction);
    }

    public static Palette[] createDefaultPalettes() {
        return new Palette[]{
                new Palette("Grayscale", new Color[]{
                        Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.DARK_GRAY
                }),
                new Palette("Fire", new Color[]{
                        new Color(0, 0, 0), new Color(50, 0, 0), new Color(100, 0, 0),
                        new Color(150, 50, 0), new Color(200, 100, 0), new Color(255, 150, 50),
                        new Color(255, 200, 100), Color.YELLOW, Color.WHITE
                }),
                new Palette("Ice", new Color[]{
                        new Color(0, 0, 50), new Color(0, 0, 100), new Color(0, 50, 150),
                        new Color(50, 100, 200), new Color(100, 150, 255), Color.CYAN, Color.WHITE
                }),
                new Palette("Aurora", new Color[]{
                        new Color(10, 10, 30), new Color(34, 59, 115), new Color(21, 125, 156),
                        new Color(58, 180, 119), new Color(195, 234, 87), new Color(255, 255, 255)
                }),
                new Palette("HSB Rainbow", generateHSBPalette(256))
        };
    }

    private static Color[] generateHSBPalette(int size) {
        Color[] palette = new Color[size];
        for (int i = 0; i < size; i++) {
            palette[i] = Color.getHSBColor((float) i / size, 1.0f, 1.0f);
        }
        return palette;
    }
}
