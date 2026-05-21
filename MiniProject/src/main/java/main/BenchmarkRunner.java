package main;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BenchmarkRunner {
    private static final Palette DEFAULT_PALETTE = Palette.createDefaultPalettes()[1];

    private BenchmarkRunner() {
    }

    public static void run(CommandLineOptions options) {
        int[] threadCounts = options.benchmarkThreadCounts();
        if (threadCounts.length == 0) {
            throw new IllegalArgumentException("At least one benchmark thread count is required.");
        }

        System.out.printf(
                "Benchmark configuration:%n  size=%dx%d%n  iterations=%d%n  smooth=%s%n  antialias=%s%n  warmup=%d%n  runs=%d%n%n",
                options.initialWidth(),
                options.initialHeight(),
                options.initialIterations(),
                options.smoothColoringEnabled() ? "ON" : "OFF",
                options.antialiasingEnabled() ? "ON" : "OFF",
                options.warmupRuns(),
                options.measuredRuns()
        );

        double baselineMs = Double.NaN;
        for (int threadCount : threadCounts) {
            double totalMs = 0.0;

            for (int runIndex = 0; runIndex < options.warmupRuns() + options.measuredRuns(); runIndex++) {
                double elapsedMs = runSingleBenchmark(options, threadCount);
                if (runIndex >= options.warmupRuns()) {
                    totalMs += elapsedMs;
                }
            }

            double averageMs = totalMs / options.measuredRuns();
            if (Double.isNaN(baselineMs)) {
                baselineMs = averageMs;
            }

            double speedup = baselineMs / averageMs;
            System.out.printf("threads=%d | avg=%.2f ms | speedup=%.2fx%n", threadCount, averageMs, speedup);
        }
    }

    private static double runSingleBenchmark(CommandLineOptions options, int threadCount) {
        BufferedImage image = new BufferedImage(
                options.initialWidth(),
                options.initialHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        int[] pixelData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        MandelbrotRenderRequest request = MandelbrotRenderRequest.defaultView(
                options.initialWidth(),
                options.initialHeight(),
                options.initialIterations(),
                DEFAULT_PALETTE,
                options.smoothColoringEnabled(),
                options.antialiasingEnabled()
        );

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread worker = new Thread(runnable, "mandelbrot-benchmark-worker");
                    worker.setDaemon(true);
                    return worker;
                }
        );
        executor.prestartAllCoreThreads();

        AtomicInteger generation = new AtomicInteger(1);
        AtomicInteger nextStartRow = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        int rowsPerBlock = Math.max(1, (int) Math.ceil((double) options.initialHeight() / (threadCount * 16.0)));

        long startNanos = System.nanoTime();
        for (int workerIndex = 0; workerIndex < threadCount; workerIndex++) {
            futures.add(executor.submit(
                    new MandelbrotTask(
                            request,
                            nextStartRow,
                            rowsPerBlock,
                            pixelData,
                            1,
                            generation::get
                    )
            ));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Benchmark interrupted.", e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Benchmark task failed.", e.getCause());
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return elapsedNanos / 1_000_000.0;
    }
}
