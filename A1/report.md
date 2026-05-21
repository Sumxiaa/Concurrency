# Assignment 1 Zixuan Xia


This repository contains my solutions for **Assignment 1** (Exercises **1.1–1.4**) implemented in Java. The goal is to practice basic multi-threading patterns (fork/join, monitor-protected shared state, producer–consumer coordination) and to measure performance under different thread counts.



---

## 1. Files

- `PrimeSearchStatic.java` — **Exercise 1.1** (static range partition)
- `PrimeSearchCounter.java` — **Exercise 1.2** (shared counter with monitor / `synchronized`)
- `ProducerConsumerDemo.java` — **Exercise 1.3** (bounded buffer with producers/consumers)

---

## 2. How to compile

From the project directory:

```bash
javac PrimeSearchStatic.java PrimeSearchCounter.java ProducerConsumerDemo.java
```

---

## 3. How to run (CLI)

### Exercise 1.1 — Static partitioning

```bash
java PrimeSearchStatic <T> <N> <printPrimes>
```

Example (used in my timing run):

```bash
java PrimeSearchStatic 4 10000000 false
```

Output example:

```
[Exercise 1.1] T=4 N=10000000 primeCount=664579 elapsedMs=295.306
```

**Note on printing:** printing primes dominates runtime for large `N`. For timing experiments, set `printPrimes=false`. For correctness demonstrations on small `N`, you can enable printing.

---

### Exercise 1.2 — Shared counter with a monitor

```bash
java PrimeSearchCounter <T> <N> <printPrimes>
```

Example (used in my timing run):

```bash
java PrimeSearchCounter 4 10000000 false
```

Output example:

```
[Exercise 1.2] T=4 N=10000000 primeCount=664579 elapsedMs=1095.705
```

---

### Exercise 1.3 — Producer–consumer bounded buffer

The assignment statement only requires arguments `(T, N)` where `T` is the number of producers and consumers (same count) and `N` is the buffer size.

In my implementation, I additionally accept a **third argument**:

- `itemsPerProducer` — how many items each producer should generate (so the demo terminates deterministically)

Run:

```bash
java ProducerConsumerDemo <T> <bufferSize> <itemsPerProducer>
```

Example (used in my run):

```bash
java ProducerConsumerDemo 4 8 20
```

End-of-run summary example:

```
[Exercise 1.3] producers=4 consumers=4 bufferSize=8 totalItems=80 elapsedMs=40.357
```

---

## 4. Implementation details

### 4.1 Exercise 1.1 — Static range splitting (fork/join style)

**Idea.** Split the integer range `[1..N]` into `T` contiguous subranges, assign one subrange per thread, and run primality testing locally.

**Work assignment.**
- Each thread `i` receives a subrange `[L_i, R_i]` computed from `N` and `T`.
- Threads do not share mutable state while searching (except optional printing).

**Aggregation.**
- Each thread maintains a local prime counter.
- The main thread `join()`s all workers and sums the counters into `primeCount`.

**Expected performance characteristics.**
- Very low synchronization overhead (only thread start/join).
- Good when the per-number work is roughly uniform (primality tests are similar cost on average).

---

### 4.2 Exercise 1.2 — Dynamic assignment via a shared counter (monitor)

**Idea.** Instead of giving each thread a fixed range, all threads repeatedly request the “next number to test” from a shared counter. This can reduce load imbalance, at the cost of synchronization.

**Monitor / synchronization.**
- A shared counter is protected using `synchronized` access (a monitor).
- Each worker performs:
  1) acquire next candidate number (monitor),
  2) test primality (no lock),
  3) repeat until past `N`.

**Expected performance characteristics.**
- Better load balancing in principle.
- But can be significantly slower when the counter is accessed extremely frequently, creating contention and serialization.

---

### 4.3 Exercise 1.3 — Producer–consumer with bounded circular buffer

**Core data structure.** A bounded circular buffer supporting:
- `put(x)` — blocks when the buffer is full
- `take()` — blocks when the buffer is empty

**Correctness requirements.**
- No overwriting when full.
- No reading nonexistent items when empty.
- No lost items / no duplicated consumption.
- Termination: all produced items are eventually consumed and threads exit cleanly.

**Synchronization approach.**
- Buffer operations are guarded by a monitor (e.g., `synchronized` methods/blocks).
- `wait()` is used to block producers when full and consumers when empty.
- `notifyAll()` is used to wake up potentially waiting threads after state changes.

**Termination.**
- With the `itemsPerProducer` argument, total work is deterministic:
  - `totalItems = T * itemsPerProducer`
- Consumers stop after consuming `totalItems` items in total (implementation-specific; e.g., using a global consumed counter or sentinel scheme).

---

## 5. Experimental results

The assignment asks to benchmark:
- `N ∈ {10,000,000; 100,000,000}`
- `T ∈ {1, 2, 4, 8, 16}`

Below are the runs I executed **so far**, based on the provided terminal output.

### 5.1 Prime search timing (N = 10,000,000; T = 4)

| Program | T | N | printPrimes | primeCount | elapsed (ms) |
|---|---:|---:|---:|---:|---:|
| PrimeSearchStatic (Ex 1.1) | 4 | 10,000,000 | false | 664,579 | 295.306 |
| PrimeSearchCounter (Ex 1.2) | 4 | 10,000,000 | false | 664,579 | 1095.705 |

**Observation.** For this setting, the counter-based approach is ~**3.7× slower** than static partitioning (1095.705 / 295.306 ≈ 3.71). This is consistent with heavy contention on the synchronized counter: each integer tested requires a monitor-protected “get next” operation, which serializes much of the execution.

### 5.2 Producer–consumer demo (T = 4, buffer = 8, itemsPerProducer = 20)

| producers | consumers | buffer size | items per producer | total items | elapsed (ms) |
|---:|---:|---:|---:|---:|---:|
| 4 | 4 | 8 | 20 | 80 | 40.357 |

The interleaved `produced ...` / `consumed ...` output confirms that:
- multiple producers and consumers run concurrently,
- consumers block until items appear,
- producers block when the buffer is full (depending on scheduling and buffer pressure).

---

## 6. How to reproduce the full benchmark grid (recommended)

To complete the assignment’s requested table for both implementations:

```bash
# Exercise 1.1
for N in 10000000 100000000; do
  for T in 1 2 4 8 16; do
    java PrimeSearchStatic $T $N false
  done
done

# Exercise 1.2
for N in 10000000 100000000; do
  for T in 1 2 4 8 16; do
    java PrimeSearchCounter $T $N false
  done
done
```

**Tip:** Avoid enabling printing for large `N`, otherwise I/O will dominate and the timing will not reflect computation/parallelism.

---

## 7. Exercise 1.4 — Amdahl’s Law decision rule

We compare:

- **Option A (uniprocessor):** 1 processor at **5** zillion instructions/s  
- **Option B (10-processor multiprocessor):** 10 processors, each **1** zillion instructions/s

Let:
- `s` = serial fraction of the program (cannot be parallelized)
- `1 - s` = parallel fraction

Assuming the serial part runs on one core at 1 zillion instr/s on the multiprocessor, and the parallel part scales ideally across 10 cores:

- Time on Option A (single fast core, normalized):
  - Both serial and parallel parts run at speed 5  
  - `T_A = s/5 + (1-s)/5 = 1/5`

- Time on Option B:
  - serial at speed 1, parallel at aggregate speed 10  
  - `T_B = s/1 + (1-s)/10 = s + (1-s)/10`

Choose the 10-processor machine when `T_B < T_A`:

`s + (1-s)/10 < 1/5`  
`10s + (1-s) < 2`  
`9s + 1 < 2`  
`9s < 1`  
`s < 1/9 ≈ 11.11%`

**Conclusion.** If the application’s serial portion is **less than ~11.11%**, the 10-processor machine is faster. If the serial portion is larger, the single fast uniprocessor can be the better choice.

---

## 8. Notes / limitations

- The reported timings are based on the shown runs. Performance will vary across machines, JVM versions, and OS scheduling.
- For fair comparisons, use the same JVM flags, disable prime printing, and (ideally) repeat runs and average elapsed times.
