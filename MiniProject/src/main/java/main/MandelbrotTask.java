package main;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class MandelbrotTask implements Runnable {
    private final MandelbrotRenderRequest request;
    private final AtomicInteger nextStartRow;
    private final int rowsPerBlock;
    private final int[] pixelData;
    private final int generation;
    private final IntSupplier currentGenerationSupplier;

    public MandelbrotTask(
            MandelbrotRenderRequest request,
            AtomicInteger nextStartRow,
            int rowsPerBlock,
            int[] pixelData,
            int generation,
            IntSupplier currentGenerationSupplier
    ) {
        this.request = request;
        this.nextStartRow = nextStartRow;
        this.rowsPerBlock = Math.max(1, rowsPerBlock);
        this.pixelData = pixelData;
        this.generation = generation;
        this.currentGenerationSupplier = currentGenerationSupplier;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted() && currentGenerationSupplier.getAsInt() == generation) {
            int startY = nextStartRow.getAndAdd(rowsPerBlock);
            if (startY >= request.imageHeight()) {
                return;
            }

            int endY = Math.min(request.imageHeight(), startY + rowsPerBlock);
            for (int y = startY; y < endY; y++) {
                if (Thread.currentThread().isInterrupted() || currentGenerationSupplier.getAsInt() != generation) {
                    return;
                }
                MandelbrotRenderer.renderRow(request, y, pixelData);
            }
        }
    }
}
