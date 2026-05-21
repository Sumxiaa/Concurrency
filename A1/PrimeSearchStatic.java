import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exercise 1.1
 * Usage: java PrimeSearchStatic <T> <N> [printPrimes]
 */
public class PrimeSearchStatic {
    private static class Worker extends Thread {
        private final int start;
        private final int end;
        private final List<Integer> localPrimes = new ArrayList<>();

        Worker(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void run() {
            for (int x = start; x <= end; x++) {
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
            System.out.println("Usage: java PrimeSearchStatic <T> <N> [printPrimes]");
            return;
        }

        int t = Integer.parseInt(args[0]);
        int n = Integer.parseInt(args[1]);
        boolean printPrimes = args.length == 3 && Boolean.parseBoolean(args[2]);

        if (t <= 0 || n < 1) {
            System.out.println("T must be > 0 and N must be >= 1");
            return;
        }

        Worker[] workers = new Worker[t];
        int baseSize = n / t;
        int remainder = n % t;
        int current = 1;

        for (int i = 0; i < t; i++) {
            int size = baseSize + (i < remainder ? 1 : 0);
            int start = current;
            int end = (size == 0) ? current - 1 : current + size - 1;
            workers[i] = new Worker(start, end);
            current = end + 1;
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
        System.out.printf("[Exercise 1.1] T=%d N=%d primeCount=%d elapsedMs=%.3f%n", t, n, allPrimes.size(), ms);
    }
}
