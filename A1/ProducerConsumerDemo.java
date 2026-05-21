import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exercise 1.3
 * Usage: java ProducerConsumerDemo <T> <N> [itemsPerProducer]
 * T: producer count and consumer count
 * N: circular buffer size
 */
public class ProducerConsumerDemo {
    private static class CircularBuffer {
        private final int[] data;
        private int head = 0;
        private int tail = 0;
        private int count = 0;

        CircularBuffer(int capacity) {
            this.data = new int[capacity];
        }

        public synchronized void put(int value) throws InterruptedException {
            while (count == data.length) {
                wait();
            }
            data[tail] = value;
            tail = (tail + 1) % data.length;
            count++;
            notifyAll();
        }

        public synchronized int take() throws InterruptedException {
            while (count == 0) {
                wait();
            }
            int value = data[head];
            head = (head + 1) % data.length;
            count--;
            notifyAll();
            return value;
        }
    }

    private static class Producer extends Thread {
        private final int producerId;
        private final int items;
        private final CircularBuffer buffer;
        private final AtomicInteger sequence;

        Producer(int producerId, int items, CircularBuffer buffer, AtomicInteger sequence) {
            this.producerId = producerId;
            this.items = items;
            this.buffer = buffer;
            this.sequence = sequence;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < items; i++) {
                    int value = sequence.incrementAndGet();
                    buffer.put(value);
                    System.out.printf("Producer-%d produced %d%n", producerId, value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class Consumer extends Thread {
        private final int consumerId;
        private final CircularBuffer buffer;

        Consumer(int consumerId, CircularBuffer buffer) {
            this.consumerId = consumerId;
            this.buffer = buffer;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int value = buffer.take();
                    if (value == Integer.MIN_VALUE) {
                        return;
                    }
                    System.out.printf("Consumer-%d consumed %d%n", consumerId, value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: java ProducerConsumerDemo <T> <N> [itemsPerProducer]");
            return;
        }

        int t = Integer.parseInt(args[0]);
        int n = Integer.parseInt(args[1]);
        int itemsPerProducer = args.length == 3 ? Integer.parseInt(args[2]) : 20;

        if (t <= 0 || n <= 0 || itemsPerProducer <= 0) {
            System.out.println("T, N, itemsPerProducer must all be > 0");
            return;
        }

        CircularBuffer buffer = new CircularBuffer(n);
        AtomicInteger sequence = new AtomicInteger(0);

        Producer[] producers = new Producer[t];
        Consumer[] consumers = new Consumer[t];

        for (int i = 0; i < t; i++) {
            producers[i] = new Producer(i, itemsPerProducer, buffer, sequence);
            consumers[i] = new Consumer(i, buffer);
        }

        long startNs = System.nanoTime();

        for (Consumer c : consumers) {
            c.start();
        }
        for (Producer p : producers) {
            p.start();
        }

        for (Producer p : producers) {
            p.join();
        }

        // poison pills: one for each consumer
        for (int i = 0; i < t; i++) {
            buffer.put(Integer.MIN_VALUE);
        }

        for (Consumer c : consumers) {
            c.join();
        }

        long endNs = System.nanoTime();
        double ms = (endNs - startNs) / 1_000_000.0;
        int totalProduced = t * itemsPerProducer;
        System.out.printf("[Exercise 1.3] producers=%d consumers=%d bufferSize=%d totalItems=%d elapsedMs=%.3f%n",
                t, t, n, totalProduced, ms);
    }
}
