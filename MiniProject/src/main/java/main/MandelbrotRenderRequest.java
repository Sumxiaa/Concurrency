package main;

public record MandelbrotRenderRequest(
        int imageWidth,
        int imageHeight,
        double minRe,
        double maxRe,
        double minIm,
        double maxIm,
        int maxIterations,
        Palette palette,
        boolean smoothColoring,
        boolean antialiasing
) {
    public MandelbrotRenderRequest {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive.");
        }
        if (maxRe <= minRe || maxIm <= minIm) {
            throw new IllegalArgumentException("The complex-plane bounds must define a non-empty area.");
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be positive.");
        }
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null.");
        }
    }

    public static MandelbrotRenderRequest defaultView(
            int imageWidth,
            int imageHeight,
            int maxIterations,
            Palette palette,
            boolean smoothColoring,
            boolean antialiasing
    ) {
        return new MandelbrotRenderRequest(
                imageWidth,
                imageHeight,
                MandelbrotExplorer.DEFAULT_MIN_RE,
                MandelbrotExplorer.DEFAULT_MAX_RE,
                MandelbrotExplorer.DEFAULT_MIN_IM,
                MandelbrotExplorer.DEFAULT_MAX_IM,
                maxIterations,
                palette,
                smoothColoring,
                antialiasing
        );
    }
}
