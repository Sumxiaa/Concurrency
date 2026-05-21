package main;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MandelbrotExplorer {
    public static final double DEFAULT_MIN_RE = -2.1;
    public static final double DEFAULT_MAX_RE = 0.7;
    public static final double DEFAULT_MIN_IM = -1.2;
    public static final double DEFAULT_MAX_IM = 1.2;
    public static final int DEFAULT_ITERATIONS = 256;
    public static final int MIN_ITERATIONS = 50;

    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
    private static final int TARGET_BLOCKS_PER_THREAD = 16;
    private static final int REPAINT_INTERVAL_MS = 40;

    private final JFrame frame;
    private final MandelbrotPanel mandelbrotPanel;
    private final Timer repaintTimer;
    private final Palette[] palettes = Palette.createDefaultPalettes();
    private final AtomicInteger renderGeneration = new AtomicInteger();

    private ThreadPoolExecutor executorService;
    private RenderSession activeRenderSession;
    private long lastComputationTime;
    private boolean rendering;

    private double currentMinRe = DEFAULT_MIN_RE;
    private double currentMaxRe = DEFAULT_MAX_RE;
    private double currentMinIm = DEFAULT_MIN_IM;
    private double currentMaxIm = DEFAULT_MAX_IM;

    private int maxIterations;
    private int numThreads;
    private int currentPaletteIndex = 1;
    private boolean smoothColoringEnabled;
    private boolean antialiasingEnabled;

    public MandelbrotExplorer(CommandLineOptions options) {
        numThreads = clampThreadCount(options.initialThreads());
        maxIterations = Math.max(MIN_ITERATIONS, options.initialIterations());
        smoothColoringEnabled = options.smoothColoringEnabled();
        antialiasingEnabled = options.antialiasingEnabled();

        mandelbrotPanel = new MandelbrotPanel(this, options.initialWidth(), options.initialHeight());
        repaintTimer = new Timer(REPAINT_INTERVAL_MS, event -> mandelbrotPanel.repaint());
        repaintTimer.setCoalesce(true);

        setupExecutorService();

        frame = new JFrame("Parallel Mandelbrot Explorer");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        frame.setLayout(new BorderLayout());
        frame.add(mandelbrotPanel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        SwingUtilities.invokeLater(this::triggerComputation);
    }

    private void setupExecutorService() {
        RenderSession previousSession = activeRenderSession;
        if (previousSession != null) {
            previousSession.cancel();
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        executorService = new ThreadPoolExecutor(
                numThreads,
                numThreads,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread worker = new Thread(runnable, "mandelbrot-worker");
                    worker.setDaemon(true);
                    return worker;
                }
        );
        executorService.prestartAllCoreThreads();
    }

    public void triggerComputation() {
        int panelWidth = Math.max(1, mandelbrotPanel.getWidth());
        int panelHeight = Math.max(1, mandelbrotPanel.getHeight());

        BufferedImage nextImage = MandelbrotPanel.createImageBuffer(panelWidth, panelHeight);
        MandelbrotRenderRequest request = new MandelbrotRenderRequest(
                panelWidth,
                panelHeight,
                currentMinRe,
                currentMaxRe,
                currentMinIm,
                currentMaxIm,
                maxIterations,
                palettes[currentPaletteIndex],
                smoothColoringEnabled,
                antialiasingEnabled
        );

        int generation = renderGeneration.incrementAndGet();
        cancelActiveRender();

        mandelbrotPanel.replaceImage(nextImage);
        rendering = true;
        if (!repaintTimer.isRunning()) {
            repaintTimer.start();
        }

        int[] pixelData = mandelbrotPanel.getImageDataArray();
        List<Future<?>> workerFutures = new ArrayList<>();
        RenderSession session = new RenderSession(generation, workerFutures);
        activeRenderSession = session;

        int rowsPerBlock = calculateRowsPerBlock(panelHeight);
        AtomicInteger nextStartRow = new AtomicInteger(0);
        for (int workerIndex = 0; workerIndex < numThreads; workerIndex++) {
            workerFutures.add(executorService.submit(
                    new MandelbrotTask(
                            request,
                            nextStartRow,
                            rowsPerBlock,
                            pixelData,
                            generation,
                            renderGeneration::get
                    )
            ));
        }

        long startTimeNanos = System.nanoTime();
        CompletableFuture.runAsync(() -> awaitRenderCompletion(session, startTimeNanos));
    }

    private void awaitRenderCompletion(RenderSession session, long startTimeNanos) {
        boolean cancelled = false;

        for (Future<?> future : session.workerFutures()) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelled = true;
                break;
            } catch (java.util.concurrent.CancellationException e) {
                cancelled = true;
                break;
            } catch (java.util.concurrent.ExecutionException e) {
                cancelled = true;
                System.err.println("Computation task failed: " + e.getCause());
                e.getCause().printStackTrace();
                break;
            }
        }

        if (renderGeneration.get() != session.generation()) {
            return;
        }

        if (!cancelled) {
            lastComputationTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        }

        SwingUtilities.invokeLater(() -> {
            if (renderGeneration.get() != session.generation()) {
                return;
            }
            rendering = false;
            activeRenderSession = null;
            repaintTimer.stop();
            mandelbrotPanel.repaint();
        });
    }

    private int calculateRowsPerBlock(int panelHeight) {
        int targetTaskCount = Math.max(numThreads, numThreads * TARGET_BLOCKS_PER_THREAD);
        return Math.max(1, (int) Math.ceil((double) panelHeight / targetTaskCount));
    }

    private void cancelActiveRender() {
        RenderSession session = activeRenderSession;
        if (session != null) {
            session.cancel();
        }
    }

    public void resetView() {
        currentMinRe = DEFAULT_MIN_RE;
        currentMaxRe = DEFAULT_MAX_RE;
        currentMinIm = DEFAULT_MIN_IM;
        currentMaxIm = DEFAULT_MAX_IM;
        maxIterations = DEFAULT_ITERATIONS;
        triggerComputation();
    }

    public void zoomToRectangle(Point p1, Point p2) {
        int panelWidth = Math.max(1, mandelbrotPanel.getWidth());
        int panelHeight = Math.max(1, mandelbrotPanel.getHeight());

        double reRange = currentMaxRe - currentMinRe;
        double imRange = currentMaxIm - currentMinIm;

        double newMinRe = currentMinRe + (Math.min(p1.x, p2.x) * reRange / panelWidth);
        double newMaxRe = currentMinRe + (Math.max(p1.x, p2.x) * reRange / panelWidth);
        double newMaxIm = currentMaxIm - (Math.min(p1.y, p2.y) * imRange / panelHeight);
        double newMinIm = currentMaxIm - (Math.max(p1.y, p2.y) * imRange / panelHeight);

        double newReRange = newMaxRe - newMinRe;
        double newImRange = newMaxIm - newMinIm;
        if (newReRange <= 0.0 || newImRange <= 0.0) {
            return;
        }

        double panelAspectRatio = (double) panelWidth / panelHeight;
        double rectAspectRatio = newReRange / newImRange;

        if (rectAspectRatio > panelAspectRatio) {
            double expectedImRange = newReRange / panelAspectRatio;
            double imCenter = (newMinIm + newMaxIm) / 2.0;
            newMinIm = imCenter - expectedImRange / 2.0;
            newMaxIm = imCenter + expectedImRange / 2.0;
        } else {
            double expectedReRange = newImRange * panelAspectRatio;
            double reCenter = (newMinRe + newMaxRe) / 2.0;
            newMinRe = reCenter - expectedReRange / 2.0;
            newMaxRe = reCenter + expectedReRange / 2.0;
        }

        currentMinRe = newMinRe;
        currentMaxRe = newMaxRe;
        currentMinIm = newMinIm;
        currentMaxIm = newMaxIm;
        triggerComputation();
    }

    public void zoomIn(Point mousePos) {
        zoom(0.8, mousePos);
    }

    public void zoomOut(Point mousePos) {
        zoom(1.25, mousePos);
    }

    private void zoom(double factor, Point mousePos) {
        double reRange = currentMaxRe - currentMinRe;
        double imRange = currentMaxIm - currentMinIm;

        double centerRe;
        double centerIm;

        if (mousePos != null) {
            centerRe = currentMinRe + (mousePos.x * reRange / Math.max(1, mandelbrotPanel.getWidth()));
            centerIm = currentMaxIm - (mousePos.y * imRange / Math.max(1, mandelbrotPanel.getHeight()));
        } else {
            centerRe = (currentMinRe + currentMaxRe) / 2.0;
            centerIm = (currentMinIm + currentMaxIm) / 2.0;
        }

        double newReRange = reRange * factor;
        double newImRange = imRange * factor;

        currentMinRe = centerRe - newReRange / 2.0;
        currentMaxRe = centerRe + newReRange / 2.0;
        currentMinIm = centerIm - newImRange / 2.0;
        currentMaxIm = centerIm + newImRange / 2.0;
        triggerComputation();
    }

    public void panView(int dx, int dy) {
        double reRange = currentMaxRe - currentMinRe;
        double imRange = currentMaxIm - currentMinIm;

        double deltaRe = dx * reRange / Math.max(1, mandelbrotPanel.getWidth());
        double deltaIm = dy * imRange / Math.max(1, mandelbrotPanel.getHeight());

        currentMinRe -= deltaRe;
        currentMaxRe -= deltaRe;
        currentMinIm += deltaIm;
        currentMaxIm += deltaIm;
        triggerComputation();
    }

    public void changeThreads(int delta) {
        int nextThreadCount = clampThreadCount(numThreads + delta);
        if (nextThreadCount == numThreads) {
            return;
        }
        numThreads = nextThreadCount;
        setupExecutorService();
        triggerComputation();
    }

    public void changeIterations(int delta) {
        maxIterations = Math.max(MIN_ITERATIONS, maxIterations + delta);
        triggerComputation();
    }

    public void changePalette(int delta) {
        currentPaletteIndex = (currentPaletteIndex + delta + palettes.length) % palettes.length;
        triggerComputation();
    }

    public void toggleAntialiasing() {
        antialiasingEnabled = !antialiasingEnabled;
        triggerComputation();
    }

    public void toggleSmoothColoring() {
        smoothColoringEnabled = !smoothColoringEnabled;
        triggerComputation();
    }

    public void saveCurrentImage() {
        if (rendering) {
            JOptionPane.showMessageDialog(
                    frame,
                    "The image is still rendering. Please wait until the computation is finished.",
                    "Rendering in progress",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        BufferedImage imageToSave = mandelbrotPanel.copyCurrentImage();
        if (imageToSave == null) {
            JOptionPane.showMessageDialog(
                    frame,
                    "No image is available to save.",
                    "Save failed",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Mandelbrot image");
        chooser.setSelectedFile(new File("mandelbrot.png"));

        int result = chooser.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File outputFile = chooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".png")) {
            outputFile = new File(outputFile.getAbsolutePath() + ".png");
        }

        try {
            ImageIO.write(imageToSave, "png", outputFile);
            JOptionPane.showMessageDialog(
                    frame,
                    "Image saved to:\n" + outputFile.getAbsolutePath(),
                    "Image saved",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Could not save image:\n" + e.getMessage(),
                    "Save failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public int getNumThreads() {
        return numThreads;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public String getCurrentPaletteName() {
        return palettes[currentPaletteIndex].getName();
    }

    public boolean isSmoothColoringEnabled() {
        return smoothColoringEnabled;
    }

    public boolean isAntialiasingEnabled() {
        return antialiasingEnabled;
    }

    public boolean isRendering() {
        return rendering;
    }

    public long getLastComputationTime() {
        return lastComputationTime;
    }

    public String getStatusText() {
        String timeText = rendering ? "rendering..." : lastComputationTime + " ms";
        return String.format(
                "Threads: %d | Iterations: %d | Palette: %s | Smooth: %s | AA5: %s | Time: %s",
                getNumThreads(),
                getMaxIterations(),
                getCurrentPaletteName(),
                isSmoothColoringEnabled() ? "ON" : "OFF",
                isAntialiasingEnabled() ? "ON" : "OFF",
                timeText
        );
    }

    public String getControlsText() {
        return "Drag: zoom | Shift+drag: pan | Wheel/I/O: zoom | Esc: reset | T/C/P/A/S: controls | Ctrl/Cmd+S: save";
    }

    public void shutdown() {
        repaintTimer.stop();
        cancelActiveRender();

        if (executorService != null) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        frame.dispose();
        System.exit(0);
    }

    private static int clampThreadCount(int threadCount) {
        return Math.max(1, Math.min(MAX_THREADS, threadCount));
    }

    public static void main(String[] args) {
        CommandLineOptions options = CommandLineOptions.parse(args);
        if (options.benchmarkMode()) {
            BenchmarkRunner.run(options);
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set system look and feel.");
        }

        SwingUtilities.invokeLater(() -> new MandelbrotExplorer(options));
    }

    private record RenderSession(int generation, List<Future<?>> workerFutures) {
        private void cancel() {
            for (Future<?> future : workerFutures) {
                future.cancel(true);
            }
        }
    }
}
