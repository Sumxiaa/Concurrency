import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exercise 1.2
 * Usage: java PrimeSearchCounter <T> <N> [printPrimes]
 */
public class PrimeSearchCounter {
    private static class NumberMonitor {
        private int current = 1;
        private final int max;

        NumberMonitor(int max) {
            this.max = max;
        }

        public synchronized int next() {
            if (current > max) {
                return -1;
            }
            return current++;
        }
    }

    private static class Worker extends Thread {
        private final NumberMonitor monitor;
        private final List<Integer> localPrimes = new ArrayList<>();

        Worker(NumberMonitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void run() {
            while (true) {
                int x = monitor.next();
                if (x == -1) {
                    return;
                }
                if (isPrime(x)) {
                    localPrimes.add(x);
                }
            }
        }

        List<Integer> getLocalPrimes() {
            return localPrimes;
        }
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if ((n & 1) == 0) return false;
        int limit = (int) Math.sqrt(n);
        for (int d = 3; d <= limit; d += 2) {
            if (n % d == 0) return false;
        }
        return true;
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: java PrimeSearchCounter <T> <N> [printPrimes]");
            return;
        }

        int t = Integer.parseInt(args[0]);
        int n = Integer.parseInt(args[1]);
        boolean printPrimes = args.length == 3 && Boolean.parseBoolean(args[2]);

        if (t <= 0 || n < 1) {
            System.out.println("T must be > 0 and N must be >= 1");
            return;
        }

        NumberMonitor monitor = new NumberMonitor(n);
        Worker[] workers = new Worker[t];
        for (int i = 0; i < t; i++) {
            workers[i] = new Worker(monitor);
        }

        long startNs = System.nanoTime();
        for (Worker w : workers) {
            w.start();
        }
        for (Worker w : workers) {
            w.join();
        }
        long endNs = System.nanoTime();

        List<Integer> allPrimes = new ArrayList<>();
        for (Worker w : workers) {
            allPrimes.addAll(w.getLocalPrimes());
        }
        Collections.sort(allPrimes);

        if (printPrimes) {
            for (int p : allPrimes) {
                System.out.println(p);
            }
        }

        double ms = (endNs - startNs) / 1_000_000.0;
        System.out.printf("[Exercise 1.2] T=%d N=%d primeCount=%d elapsedMs=%.3f%n", t, n, allPrimes.size(), ms);
    }
}
