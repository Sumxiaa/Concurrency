package main;

public final class MandelbrotRenderer {
    private static final double ESCAPE_RADIUS_SQUARED = 4.0;
    private static final double LOG_2 = Math.log(2.0);
    private static final double[][] ANTIALIASING_OFFSETS = {
            {0.50, 0.50},
            {0.25, 0.25},
            {0.75, 0.25},
            {0.25, 0.75},
            {0.75, 0.75}
    };

    private MandelbrotRenderer() {
    }

    public static void renderRow(MandelbrotRenderRequest request, int y, int[] pixelData) {
        int width = request.imageWidth();
        int rowOffset = y * width;

        for (int x = 0; x < width; x++) {
            pixelData[rowOffset + x] = request.antialiasing()
                    ? computeAntialiasedPixel(request, x, y)
                    : computePixel(request, x + 0.5, y + 0.5);
        }
    }

    private static int computeAntialiasedPixel(MandelbrotRenderRequest request, int x, int y) {
        int totalRed = 0;
        int totalGreen = 0;
        int totalBlue = 0;

        for (double[] offset : ANTIALIASING_OFFSETS) {
            int rgb = computePixel(request, x + offset[0], y + offset[1]);
            totalRed += (rgb >> 16) & 0xFF;
            totalGreen += (rgb >> 8) & 0xFF;
            totalBlue += rgb & 0xFF;
        }

        int sampleCount = ANTIALIASING_OFFSETS.length;
        return ((totalRed / sampleCount) << 16)
                | ((totalGreen / sampleCount) << 8)
                | (totalBlue / sampleCount);
    }

    private static int computePixel(MandelbrotRenderRequest request, double pixelX, double pixelY) {
        double reRange = request.maxRe() - request.minRe();
        double imRange = request.maxIm() - request.minIm();
        double cx = request.minRe() + (pixelX * reRange / request.imageWidth());
        double cy = request.maxIm() - (pixelY * imRange / request.imageHeight());

        double zx = 0.0;
        double zy = 0.0;
        double zxSquared = 0.0;
        double zySquared = 0.0;
        int iterations = 0;

        while (zxSquared + zySquared <= ESCAPE_RADIUS_SQUARED && iterations < request.maxIterations()) {
            zy = (2.0 * zx * zy) + cy;
            zx = (zxSquared - zySquared) + cx;
            zxSquared = zx * zx;
            zySquared = zy * zy;
            iterations++;
        }

        double smoothIteration = iterations;
        if (request.smoothColoring() && iterations < request.maxIterations()) {
            double logModulus = 0.5 * Math.log(zxSquared + zySquared);
            smoothIteration = iterations + 1.0 - Math.log(logModulus / LOG_2) / LOG_2;
        }

        return request.palette().getColor(iterations, request.maxIterations(), smoothIteration, request.smoothColoring());
    }
}
