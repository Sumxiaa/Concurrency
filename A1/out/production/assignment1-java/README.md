# Assignment 1 Java Solutions

## Files
- `PrimeSearchStatic.java` -> Exercise 1.1
- `PrimeSearchCounter.java` -> Exercise 1.2
- `ProducerConsumerDemo.java` -> Exercise 1.3

## Compile
```bash
cd /Users/xiazixuan/Documents/New\ project/assignment1-java
javac PrimeSearchStatic.java PrimeSearchCounter.java ProducerConsumerDemo.java
```

## Run examples
Exercise 1.1:
```bash
java PrimeSearchStatic 4 10000000 false
```

Exercise 1.2:
```bash
java PrimeSearchCounter 4 10000000 false
```

Exercise 1.3:
```bash
java ProducerConsumerDemo 4 8 20
```

`printPrimes` 建议在大 N 时设为 `false`，否则输出会非常大，显著影响计时。

## Exercise 1.4 (Amdahl's Law)
设串行比例为 `s`，并行比例为 `1-s`。
- 单核机器速度：`5`（zillion instructions/s）
- 10 核机器速度：`10`（每核 1，总计 10，忽略通信开销）

对基线归一化后：
- 单核执行时间 `T_uni = s/5 + (1-s)/5 = 1/5`
- 10 核执行时间 `T_multi = s/1 + (1-s)/10`

选择 10 核当且仅当：
`T_multi < T_uni`
=> `s + (1-s)/10 < 1/5`
=> `9s + 1 < 2`
=> `s < 1/9 ≈ 11.11%`

结论：如果应用串行部分小于约 11.11%，10 核更好；否则 5 倍速单核更好。
