# Parallel Mandelbrot Project

这个项目实现了一个并行的 Mandelbrot fractal generator，支持：

- 多线程并行渲染
- 基于 `AtomicInteger` 的 dynamic row-block scheduling
- 鼠标框选缩放、平移、滚轮缩放
- 可切换迭代次数、调色板、线程数
- 平滑着色
- 真正的 5-point antialiasing
- `Ctrl+S` 保存当前图像为 PNG
- 可复现的命令行 benchmark
- 自动生成 speedup plot 的 Python 脚本

项目要求来自 `Project.pdf`，核心评分点是：

- 正确性
- 速度
- 代码质量
- 功能和界面

这版代码专门围绕这四项做了优化。

## 1. 环境要求

- JDK 21 或更高版本
- macOS / Linux / Windows 都可以，只要有 `java`、`javac`、`jar`

先检查 Java：

```bash
java -version
javac -version
jar --version
```

## 2. 项目结构

主要代码都在：

- [src/main/java/main/MandelbrotExplorer.java](/Users/xiazixuan/Desktop/MiniProject/src/main/java/main/MandelbrotExplorer.java)
- [src/main/java/main/MandelbrotPanel.java](/Users/xiazixuan/Desktop/MiniProject/src/main/java/main/MandelbrotPanel.java)
- [src/main/java/main/MandelbrotRenderer.java](/Users/xiazixuan/Desktop/MiniProject/src/main/java/main/MandelbrotRenderer.java)
- [src/main/java/main/MandelbrotTask.java](/Users/xiazixuan/Desktop/MiniProject/src/main/java/main/MandelbrotTask.java)
- [src/main/java/main/BenchmarkRunner.java](/Users/xiazixuan/Desktop/MiniProject/src/main/java/main/BenchmarkRunner.java)
- [scripts/plot_speedup.py](/Users/xiazixuan/Desktop/MiniProject/scripts/plot_speedup.py)

## 3. 一步步编译

### 方法 A：直接用脚本编译

在项目根目录运行：

```bash
chmod +x build.sh
./build.sh
```

成功后会生成：

```bash
target/MiniProject-1.0-SNAPSHOT.jar
```

### 方法 B：如果你本机装了 Maven

```bash
mvn package
```

## 4. 运行 GUI 程序

最简单的运行方式：

```bash
java -jar target/MiniProject-1.0-SNAPSHOT.jar
```

如果你想指定初始线程数、窗口大小或迭代次数：

```bash
java -jar target/MiniProject-1.0-SNAPSHOT.jar --threads 8 --iterations 800 --width 1200 --height 800
```

## 5. GUI 操作说明

### 鼠标

- 左键拖拽：框选区域并放大
- `Shift + 左键拖拽`：平移视图
- 鼠标滚轮向上：以鼠标位置为中心放大
- 鼠标滚轮向下：以鼠标位置为中心缩小

### 键盘

- `Escape`：重置视图
- `I`：放大
- `O`：缩小
- `T`：增加线程数
- `Shift + T`：减少线程数
- `C`：增加最大迭代次数
- `Shift + C`：减少最大迭代次数
- `P`：切换到下一个调色板
- `Shift + P`：切换到上一个调色板
- `A`：开启或关闭 5-point antialiasing
- `S`：开启或关闭 smooth coloring
- `Ctrl + S`：把当前渲染好的图像保存成 PNG
- `Cmd + S`：在 macOS 上也可以直接保存 PNG

界面底部会实时显示：

- 当前线程数
- 当前迭代次数
- 当前调色板
- smooth coloring 状态
- 5-point antialiasing 状态
- 本次渲染耗时

## 6. 如何做实验

老师最关心的是 4 核相对 1 核是否至少达到 `2x`，理想情况接近 `3x`。最稳妥的方法是直接跑项目自带 benchmark。

### 推荐实验命令

```bash
java -jar target/MiniProject-1.0-SNAPSHOT.jar --benchmark --threads 1,2,4,8 --iterations 1200 --width 1600 --height 1200 --runs 5 --warmup 1
```

如果你机器比较慢，可以稍微降低尺寸：

```bash
java -jar target/MiniProject-1.0-SNAPSHOT.jar --benchmark --threads 1,2,4 --iterations 1000 --width 1200 --height 900 --runs 5 --warmup 1
```

### benchmark 输出示例

程序会输出类似：

```text
Benchmark configuration:
  size=1600x1200
  iterations=1200
  smooth=ON
  antialias=OFF
  warmup=1
  runs=5

threads=1 | avg=XXX.XX ms | speedup=1.00x
threads=2 | avg=XXX.XX ms | speedup=X.XXx
threads=4 | avg=XXX.XX ms | speedup=X.XXx
threads=8 | avg=XXX.XX ms | speedup=X.XXx
```

### 你应该记录什么

把下面这些写进报告：

- 测试机器 CPU 型号
- 操作系统
- Java 版本
- benchmark 命令
- `1`、`2`、`4`、`8` 线程的平均时间
- 4 线程相对 1 线程的 speedup

### 生成 speedup 图

先把 benchmark 输出保存到文件：

```bash
mkdir -p benchmark-results
java -jar target/MiniProject-1.0-SNAPSHOT.jar --benchmark --threads 1,2,4,8 --iterations 1200 --width 1600 --height 1200 --runs 5 --warmup 1 > benchmark-results/latest.txt
```

然后生成折线图：

```bash
python3 scripts/plot_speedup.py benchmark-results/latest.txt
```

生成的图片默认在：

```bash
figures/speedup.png
```

如果你已经手工整理好了线程数和时间，也可以直接传参数：

```bash
python3 scripts/plot_speedup.py --threads 1,2,4,8 --times 2542.87,1300.00,637.85,420.00
```

## 7. 高分建议

为了更容易拿高分，可以采取建议：

1. 先说明并行策略。
   使用线程池，把图像按水平条带切分成多个任务并行计算。

2. 强调正确性。
   这版代码用“渲染代次”隔离旧任务和新任务，连续缩放、拖动时不会让旧任务污染新结果。

3. 强调性能优化。
   这版实现不再依赖 `Complex` 对象，而是直接使用原始 `double` 运算，减少了对象分配和数学开销。

4. 强调功能完整。
   不只是能并行算，还支持平移、缩放、平滑着色、调色板切换、线程调节、PNG 导出和真正的 5 点抗锯齿。

5. benchmark 时先关掉 antialiasing。
   因为 `A` 会做 5 个采样点，画质更好，但速度会慢一些。做 speedup 对比时建议统一设置，例如：
   `smooth=ON, antialias=OFF`

6. 强调动态调度。
   现在不是把固定大块图像永久分配给某个线程，而是让 worker 通过原子计数器动态领取下一个 row block，更容易解释负载均衡。

## 8. 常见问题

### 编译时报 `javac: command not found`

说明没有装 JDK，先安装 JDK 21+。

### 双击 jar 没反应

请用终端运行：

```bash
java -jar target/MiniProject-1.0-SNAPSHOT.jar
```

### benchmark 太快，数据不稳定

把这几个参数调大：

- `--iterations`
- `--width`
- `--height`
- `--runs`

### 开启 antialiasing 后变慢

这是正常的，因为它现在是真正的 5-point antialiasing，而不是单纯的 Swing 显示插值。


